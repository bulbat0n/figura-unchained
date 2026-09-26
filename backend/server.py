import hashlib
import uuid
import os
import re
import sys
import time
import secrets
import atexit
import sqlite3
import bcrypt
import jwt
from datetime import datetime, timedelta, timezone
from aiohttp import web, WSMsgType
from dotenv import load_dotenv

if not os.path.exists(".env"):
    print("[FATAL ERROR] The .env file is missing!")
    print("Please create it or rename '.env.example' to '.env' before starting the server.")
    sys.exit(1)

load_dotenv()

REQUIRED_VARS = ["PORT", "DEBUG", "AVATAR_DIR", "MAX_PING_BPS", "MAX_PING_SIZE", "MAX_AVATAR_SIZE", "REQUIRE_AUTH"]
missing_vars = [var for var in REQUIRED_VARS if not os.environ.get(var)]

if missing_vars:
    print(f"[FATAL ERROR] Missing required variables in .env: {', '.join(missing_vars)}")
    sys.exit(1)

config_errors = []

try:
    PORT = int(os.environ.get("PORT"))
except ValueError:
    config_errors.append("PORT must be an integer.")

DEBUG_env = os.environ.get("DEBUG").lower()
if DEBUG_env not in ["true", "false"]:
    config_errors.append(f"DEBUG must be 'true' or 'false', got '{DEBUG_env}'")
else:
    DEBUG = DEBUG_env == "true"
    
REQUIRE_AUTH_env = os.environ.get("REQUIRE_AUTH", "").lower()
if REQUIRE_AUTH_env not in ["true", "false"]:
    config_errors.append(f"REQUIRE_AUTH must be 'true' or 'false', got '{REQUIRE_AUTH_env}'")
else:
    REQUIRE_AUTH = REQUIRE_AUTH_env == "true"

try:
    MAX_PING_BPS = int(os.environ.get("MAX_PING_BPS"))
except ValueError:
    config_errors.append("MAX_PING_BPS must be an integer.")

try:
    MAX_PING_SIZE = int(os.environ.get("MAX_PING_SIZE"))
except ValueError:
    config_errors.append("MAX_PING_SIZE must be an integer.")

try:
    MAX_AVATAR_SIZE = int(os.environ.get("MAX_AVATAR_SIZE"))
except ValueError:
    config_errors.append("MAX_AVATAR_SIZE must be an integer.")

AVATAR_DIR = os.environ.get("AVATAR_DIR")

if config_errors:
    print("[FATAL ERROR] .env syntax/value errors found:")
    for err in config_errors:
        print(f"  - {err}")
    sys.exit(1)

if not os.path.exists(AVATAR_DIR):
    os.makedirs(AVATAR_DIR)

os.makedirs("data", exist_ok=True)

db_conn = sqlite3.connect('data/users.db', check_same_thread=False)
db_conn.execute('CREATE TABLE IF NOT EXISTS users (uuid TEXT PRIMARY KEY, password_hash TEXT)')
db_conn.commit()

ADMIN_SESSION_TOKEN = secrets.token_hex(32)
with open("data/.admin_session", "w") as f:
    f.write(ADMIN_SESSION_TOKEN)

JWT_SECRET_FILE = "data/.jwt_secret"
if not os.path.exists(JWT_SECRET_FILE):
    JWT_SECRET = secrets.token_hex(64)
    with open(JWT_SECRET_FILE, "w") as f:
        f.write(JWT_SECRET)
else:
    with open(JWT_SECRET_FILE, "r") as f:
        JWT_SECRET = f.read().strip()

def cleanup_admin_session():
    if os.path.exists("data/.admin_session"):
        os.remove("data/.admin_session")

atexit.register(cleanup_admin_session)

UUID_REGEX = re.compile(r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$')

def is_valid_uuid(val):
    return bool(val and UUID_REGEX.match(val))

def log_info(msg):
    now = datetime.now().strftime('%H:%M:%S.%f')[:-3]
    print(f"[{now}] {msg}")

def log_debug(msg):
    if DEBUG:
        now = datetime.now().strftime('%H:%M:%S.%f')[:-3]
        print(f"[{now}] [DEBUG] {msg}")

def hex_dump(data):
    return data.hex().upper()

def get_file_hash(filepath):
    hasher = hashlib.sha256()
    with open(filepath, 'rb') as f:
        hasher.update(f.read())
    return hasher.hexdigest()

def check_auth(request, client_uuid):
    if not REQUIRE_AUTH:
        return True
        
    token = None
    auth_header = request.headers.get("Authorization", "")
    if auth_header.startswith("Bearer "):
        token = auth_header.split(" ")[1]
    else:
        raw_token = request.headers.get("token", "")
        if ":" in raw_token:
            token = raw_token.split(":", 1)[1]
            
    if token:
        try:
            payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
            return payload.get("uuid") == client_uuid
        except (jwt.ExpiredSignatureError, jwt.InvalidTokenError):
            return False
    return False

active_connections = {}
subscriptions = {}
users_with_avatars = set()

@web.middleware
async def request_logger(request, handler):
    ignore_logs = ['/api/version', '/api/limits', '/api/motd', '/api/', '/api', '/api/auth/register', '/api/auth/login']
    if request.path not in ignore_logs:
        log_debug(f"[HTTP] {request.method} {request.path} | From: {request.remote}")
    return await handler(request)

async def handle_api_check(request):
    return web.json_response({"status": "ok"})

async def handle_version(request):
    return web.json_response({"release": "0.1.5", "prerelease": "0.1.5"})

async def handle_limits(request):
    return web.json_response({"rate": {"upload": MAX_PING_BPS, "download": MAX_PING_BPS}, "limits": {"maxAvatarSize": MAX_AVATAR_SIZE}})

async def handle_motd(request):
    return web.json_response({"text": "Figura Unchained Backend", "color": "gold"})

async def handle_register(request):
    body = await request.json()
    client_uuid = body.get("uuid")
    client_hash = body.get("hash")
    
    if not is_valid_uuid(client_uuid) or not client_hash:
        return web.json_response({"error": "Invalid data format"}, status=400)
        
    cursor = db_conn.cursor()
    cursor.execute("SELECT uuid FROM users WHERE uuid = ?", (client_uuid,))
    if cursor.fetchone():
        return web.json_response({"error": "Already registered. Please login."}, status=409)
        
    hashed_pw = bcrypt.hashpw(client_hash.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    cursor.execute("INSERT INTO users (uuid, password_hash) VALUES (?, ?)", (client_uuid, hashed_pw))
    db_conn.commit()
    
    token = jwt.encode({"uuid": client_uuid, "exp": datetime.now(timezone.utc) + timedelta(days=30)}, JWT_SECRET, algorithm="HS256")
    log_info(f"[AUTH] Registered UUID {client_uuid[:8]}")
    return web.json_response({"status": "success", "token": token})

async def handle_login(request):
    body = await request.json()
    client_uuid = body.get("uuid")
    client_hash = body.get("hash")
    
    if not is_valid_uuid(client_uuid) or not client_hash:
        return web.json_response({"error": "Invalid data format"}, status=400)
        
    cursor = db_conn.cursor()
    cursor.execute("SELECT password_hash FROM users WHERE uuid = ?", (client_uuid,))
    row = cursor.fetchone()
    
    if not row or not bcrypt.checkpw(client_hash.encode('utf-8'), row[0].encode('utf-8')):
        return web.json_response({"error": "Invalid password or UUID"}, status=401)
        
    token = jwt.encode({"uuid": client_uuid, "exp": datetime.now(timezone.utc) + timedelta(days=30)}, JWT_SECRET, algorithm="HS256")
    log_info(f"[AUTH] Logged in UUID {client_uuid[:8]}")
    return web.json_response({"status": "success", "token": token})

async def handle_user_profile(request):
    target_uuid = os.path.basename(request.match_info['uuid'])
    file_path = os.path.join(AVATAR_DIR, f"{target_uuid}.nbt")
    equipped = []
    if os.path.exists(file_path):
        real_hash = get_file_hash(file_path)
        equipped.append({"owner": target_uuid, "id": "avatar", "hash": real_hash})
        log_debug(f"[PROFILER] Serving profile {target_uuid[:8]}")
    return web.json_response({"equipped": equipped, "equippedBadges": {"pride": [], "special": []}})

async def download_avatar(request):
    target_uuid = os.path.basename(request.match_info['uuid'])
    file_path = os.path.join(AVATAR_DIR, f"{target_uuid}.nbt")
    if os.path.exists(file_path):
        log_debug(f"[DOWNLOAD] Serving file: {target_uuid}.nbt")
        return web.FileResponse(file_path)
    return web.json_response({"error": "Not found"}, status=404)

async def upload_avatar(request):
    raw_token = request.headers.get('token', '')
    client_uuid = raw_token.split(':')[0] if ':' in raw_token else raw_token
    
    if not is_valid_uuid(client_uuid):
        return web.json_response({"error": "Invalid token"}, status=400)
        
    if not check_auth(request, client_uuid):
        return web.json_response({"error": "Unauthorized. Please login."}, status=401)

    data = await request.read()
    
    if len(data) == 0:
        return web.json_response({"error": "Empty file"}, status=400)
        
    if len(data) > MAX_AVATAR_SIZE:
        return web.json_response({"error": "File exceeds MAX_AVATAR_SIZE"}, status=413)

    file_path = os.path.join(AVATAR_DIR, f"{client_uuid}.nbt")
    with open(file_path, 'wb') as f:
        f.write(data)
        
    users_with_avatars.add(client_uuid)
    log_info(f"[UPLOAD] UUID {client_uuid[:8]} saved skin ({len(data)} bytes).")

    if client_uuid in subscriptions:
        uuid_bytes = uuid.UUID(client_uuid).bytes
        event_packet = b'\x02' + uuid_bytes
        for sub_uuid in subscriptions[client_uuid]:
            if sub_uuid != client_uuid and sub_uuid in active_connections:
                await active_connections[sub_uuid].send_bytes(event_packet)
                log_debug(f"[EVENT-OUT] Skin update -> {sub_uuid[:8]}")

    return web.json_response({"status": "success"})

async def equip_avatar(request):
    return web.json_response({"status": "success"})

async def delete_avatar(request):
    raw_token = request.headers.get('token', '')
    client_uuid = raw_token.split(':')[0] if ':' in raw_token else raw_token
    
    if not is_valid_uuid(client_uuid):
        return web.json_response({"error": "Invalid token"}, status=400)
        
    if not check_auth(request, client_uuid):
        return web.json_response({"error": "Unauthorized. Please login."}, status=401)

    file_path = os.path.join(AVATAR_DIR, f"{client_uuid}.nbt")
    if os.path.exists(file_path):
        os.remove(file_path)
        users_with_avatars.discard(client_uuid)
        log_info(f"[DELETE] Deleted skin for: {client_uuid[:8]}")
        
        if client_uuid in subscriptions:
            uuid_bytes = uuid.UUID(client_uuid).bytes
            event_packet = b'\x02' + uuid_bytes
            for sub_uuid in subscriptions[client_uuid]:
                if sub_uuid != client_uuid and sub_uuid in active_connections:
                    await active_connections[sub_uuid].send_bytes(event_packet)
                    log_debug(f"[EVENT-OUT] Skin deletion -> {sub_uuid[:8]}")
                        
    return web.json_response({"status": "deleted"})

async def handle_admin_broadcast(request):
    proxy_headers = ["X-Forwarded-For", "X-Real-IP", "Forwarded", "True-Client-IP", "CF-Connecting-IP", "X-Client-IP"]
    if any(h in request.headers for h in proxy_headers):
        return web.json_response({"error": "Network block"}, status=403)
        
    if request.remote not in ['127.0.0.1', '::1', 'localhost']:
        return web.json_response({"error": "Network block"}, status=403)
        
    auth_header = request.headers.get("Authorization")
    if auth_header != f"Bearer {ADMIN_SESSION_TOKEN}":
        return web.json_response({"error": "Unauthorized"}, status=401)
        
    body = await request.json()
    b_type = body.get("type")
    
    packet = None
    if b_type == "chat":
        msg = body.get("message", "")
        packet = b'\x04' + msg.encode('utf-8')
    elif b_type == "toast":
        t_type = int(body.get("toast_type", 0))
        title = body.get("title", "")
        desc = body.get("desc", "")
        packet = b'\x03' + bytes([t_type]) + title.encode('utf-8') + b'\0' + desc.encode('utf-8')
    else:
        return web.json_response({"error": "Invalid type."}, status=400)
        
    count = 0
    for ws in active_connections.values():
        await ws.send_bytes(packet)
        count += 1
        
    log_info(f"[ADMIN] Broadcasted {b_type} to {count} users.")
    return web.json_response({"status": "success", "broadcasted_to": count})

async def websocket_handler(request):
    ws = web.WebSocketResponse(heartbeat=25.0)
    await ws.prepare(request)
    
    raw_token = request.headers.get('token', '')
    client_uuid = raw_token.split(':')[0] if ':' in raw_token else raw_token
    
    if not is_valid_uuid(client_uuid):
        log_info(f"[WS] Connection rejected (invalid token): {request.remote}")
        return ws
        
    is_authenticated = check_auth(request, client_uuid)

    file_path = os.path.join(AVATAR_DIR, f"{client_uuid}.nbt")
    if os.path.exists(file_path):
        users_with_avatars.add(client_uuid)
    else:
        users_with_avatars.discard(client_uuid)

    active_connections[client_uuid] = ws
    log_info(f"[WS] + Connected {client_uuid[:8]} (Auth: {is_authenticated}). Online: {len(active_connections)}")
    
    ping_bytes_sec = 0
    ping_reset_time = time.time()
    
    try:
        await ws.send_bytes(b'\x00')
        
        if REQUIRE_AUTH:
            if not is_authenticated:
                toast_packet = b'\x03\x02' + "Auth Required".encode('utf-8') + b'\x00' + "Type /figura-unchained register <password>".encode('utf-8')
                await ws.send_bytes(toast_packet)
            else:
                toast_packet = b'\x03\x00' + "Authenticated".encode('utf-8') + b'\x00' + "Connected securely.".encode('utf-8')
                await ws.send_bytes(toast_packet)
        
        async for msg in ws:
            if msg.type == WSMsgType.BINARY:
                data = msg.data
                cmd = data[0]
                
                if cmd == 0:
                    log_debug(f"[WS-TOKEN] Ignored token packet from {client_uuid[:8]}")
                    
                elif cmd == 1:
                    if not is_authenticated:
                        toast_packet = b'\x03\x02' + "Auth Required".encode('utf-8') + b'\x00' + "Please login to animate.".encode('utf-8')
                        await ws.send_bytes(toast_packet)
                        continue

                    if client_uuid not in users_with_avatars:
                        continue
                        
                    if len(data) > MAX_PING_SIZE:
                        log_debug(f"[WS-PING-LIMIT] {client_uuid[:8]} exceeded size ({len(data)} bytes)")
                        await ws.send_bytes(b'\x05\x00') 
                        continue
                        
                    current_time = time.time()
                    if current_time - ping_reset_time > 1.0:
                        ping_bytes_sec = 0
                        ping_reset_time = current_time
                        
                    ping_bytes_sec += len(data)
                    if ping_bytes_sec > MAX_PING_BPS:
                        log_debug(f"[WS-PING-LIMIT] {client_uuid[:8]} exceeded rate limit")
                        await ws.send_bytes(b'\x05\x01')
                        continue

                    log_debug(f"[WS-PING-IN] From {client_uuid[:8]} | RAW: {hex_dump(data)}")
                    uuid_bytes = uuid.UUID(client_uuid).bytes
                    s2c_packet = data[0:1] + uuid_bytes + data[1:]
                    
                    if client_uuid in subscriptions:
                        for sub_uuid in subscriptions[client_uuid]:
                            if sub_uuid != client_uuid and sub_uuid in active_connections:
                                await active_connections[sub_uuid].send_bytes(s2c_packet)
                                log_debug(f"[WS-PING-OUT] To {sub_uuid[:8]} | RAW: {hex_dump(s2c_packet)}")
                
                elif cmd == 2 and len(data) >= 17:
                    target_uuid = str(uuid.UUID(bytes=data[1:17]))
                    if target_uuid not in subscriptions:
                        subscriptions[target_uuid] = set()
                    subscriptions[target_uuid].add(client_uuid)
                    log_debug(f"[WS-SUB] {client_uuid[:8]} -> {target_uuid[:8]}")
                
                elif cmd == 3 and len(data) >= 17:
                    target_uuid = str(uuid.UUID(bytes=data[1:17]))
                    if target_uuid in subscriptions and client_uuid in subscriptions[target_uuid]:
                        subscriptions[target_uuid].remove(client_uuid)
                        log_debug(f"[WS-UNSUB] {client_uuid[:8]} -/-> {target_uuid[:8]}")
                
            elif msg.type == WSMsgType.ERROR:
                log_info(f"[WS] Error in {client_uuid[:8]}: {ws.exception()}")
    finally:
        if client_uuid in active_connections:
            del active_connections[client_uuid]
        for target in list(subscriptions.keys()):
            if client_uuid in subscriptions[target]:
                subscriptions[target].remove(client_uuid)
        log_info(f"[WS] - Disconnected {client_uuid[:8]}. Online: {len(active_connections)}")

    return ws

app = web.Application(middlewares=[request_logger])

app.router.add_get('/api/', handle_api_check)
app.router.add_get('/api', handle_api_check)
app.router.add_get('/api/version', handle_version)
app.router.add_get('/api/limits', handle_limits)
app.router.add_get('/api/motd', handle_motd)
app.router.add_post('/api/auth/register', handle_register)
app.router.add_post('/api/auth/login', handle_login)
app.router.add_get(r'/api/{uuid:[0-9a-fA-F\-]{36}}', handle_user_profile)
app.router.add_get(r'/api/avatar/{uuid:[0-9a-fA-F\-]{36}}', download_avatar)
app.router.add_get(r'/assets/v2/{uuid:[0-9a-fA-F\-]{36}}', download_avatar)
app.router.add_get(r'/api/{uuid:[0-9a-fA-F\-]{36}}/avatar', download_avatar)
app.router.add_put('/api/avatar', upload_avatar)
app.router.add_post('/api/equip', equip_avatar)
app.router.add_delete('/api/avatar', delete_avatar)
app.router.add_post('/api/admin/broadcast', handle_admin_broadcast)
app.router.add_get('/ws', websocket_handler)
app.router.add_get('/api/ws', websocket_handler)
app.router.add_get('/api//ws', websocket_handler)

if __name__ == '__main__':
    try:
        log_info(f"Starting Server on port {PORT}...")
        web.run_app(app, port=PORT, print=None)
    except OSError as e:
        if e.errno in (98, 10048):
            print(f"\n[FATAL ERROR] Port {PORT} is already in use!")
            print("HOW TO FIX THIS:")
            print(f"1. Close the program holding port {PORT}")
            print("2. OR open .env, change PORT to a different number")
            print("   and update the 'Server IP' in Figura mod settings to localhost:new_port")
            print("\nExiting...")
        else:
            raise
