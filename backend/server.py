import hashlib
import uuid
import os
import re
from datetime import datetime
from aiohttp import web, WSMsgType
from dotenv import load_dotenv

load_dotenv()

PORT = int(os.environ["PORT"])
DEBUG = os.environ.get("DEBUG", "false").lower() == "true"
AVATAR_DIR = os.environ.get("AVATAR_DIR", "avatars")

if not os.path.exists(AVATAR_DIR):
    os.makedirs(AVATAR_DIR)

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

active_connections = {}
subscriptions = {}

@web.middleware
async def request_logger(request, handler):
    ignore_logs = ['/api/version', '/api/limits', '/api/motd', '/api/', '/api']
    if request.path not in ignore_logs:
        log_debug(f"[HTTP] {request.method} {request.path} | From: {request.remote}")
    return await handler(request)

async def handle_api_check(request):
    return web.json_response({"status": "ok"})

async def handle_version(request):
    return web.json_response({"release": "0.1.5", "prerelease": "0.1.5"})

async def handle_limits(request):
    return web.json_response({"rate": {"upload": 1000000, "download": 1000000}, "limits": {"maxAvatarSize": 10000000}})

async def handle_motd(request):
    return web.json_response({"text": "Figura Unchained Backend", "color": "gold"})

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
    client_uuid = request.headers.get('token')
    if not is_valid_uuid(client_uuid):
        return web.json_response({"error": "Invalid or missing UUID token"}, status=400)

    file_path = os.path.join(AVATAR_DIR, f"{client_uuid}.nbt")
    data = await request.read()
    
    if len(data) == 0:
        return web.json_response({"error": "Empty file"}, status=400)

    with open(file_path, 'wb') as f:
        f.write(data)
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
    client_uuid = request.headers.get('token')
    if not is_valid_uuid(client_uuid):
        return web.json_response({"error": "Invalid or missing UUID token"}, status=400)

    file_path = os.path.join(AVATAR_DIR, f"{client_uuid}.nbt")
    if os.path.exists(file_path):
        os.remove(file_path)
        log_info(f"[DELETE] Deleted skin for: {client_uuid[:8]}")
        
        if client_uuid in subscriptions:
            uuid_bytes = uuid.UUID(client_uuid).bytes
            event_packet = b'\x02' + uuid_bytes
            for sub_uuid in subscriptions[client_uuid]:
                if sub_uuid != client_uuid and sub_uuid in active_connections:
                    await active_connections[sub_uuid].send_bytes(event_packet)
                    log_debug(f"[EVENT-OUT] Skin deletion -> {sub_uuid[:8]}")
                        
    return web.json_response({"status": "deleted"})

async def websocket_handler(request):
    ws = web.WebSocketResponse(heartbeat=25.0)
    await ws.prepare(request)
    
    client_uuid = request.headers.get('token')
    if not is_valid_uuid(client_uuid):
        log_info(f"[WS] Connection rejected (invalid token): {request.remote}")
        return ws

    active_connections[client_uuid] = ws
    log_info(f"[WS] + Connected {client_uuid[:8]}. Online: {len(active_connections)}")
    
    try:
        await ws.send_bytes(b'\x00')
        
        async for msg in ws:
            if msg.type == WSMsgType.BINARY:
                data = msg.data
                cmd = data[0]
                
                if cmd == 1:
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
                    log_debug(f"[WS-SUB] {client_uuid[:8]} -> {target_uuid[:8]} | RAW: {hex_dump(data)}")
                    
                    uuid_bytes = uuid.UUID(target_uuid).bytes
                    event_packet = b'\x02' + uuid_bytes
                    await ws.send_bytes(event_packet)
                    log_debug(f"[EVENT-SYNC] Auto-sync sent -> {client_uuid[:8]}")
                
                elif cmd == 3 and len(data) >= 17:
                    target_uuid = str(uuid.UUID(bytes=data[1:17]))
                    if target_uuid in subscriptions and client_uuid in subscriptions[target_uuid]:
                        subscriptions[target_uuid].remove(client_uuid)
                        log_debug(f"[WS-UNSUB] {client_uuid[:8]} -/-> {target_uuid[:8]} | RAW: {hex_dump(data)}")
                
                else:
                    log_debug(f"[WS-UNKNOWN] CMD: {cmd} | From {client_uuid[:8]} | RAW: {hex_dump(data)}")
                    
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
app.router.add_get(r'/api/{uuid:[0-9a-fA-F\-]{36}}', handle_user_profile)
app.router.add_get(r'/api/avatar/{uuid:[0-9a-fA-F\-]{36}}', download_avatar)
app.router.add_get(r'/assets/v2/{uuid:[0-9a-fA-F\-]{36}}', download_avatar)
app.router.add_get(r'/api/{uuid:[0-9a-fA-F\-]{36}}/avatar', download_avatar)
app.router.add_put('/api/avatar', upload_avatar)
app.router.add_post('/api/equip', equip_avatar)
app.router.add_delete('/api/avatar', delete_avatar)
app.router.add_get('/ws', websocket_handler)
app.router.add_get('/api/ws', websocket_handler)
app.router.add_get('/api//ws', websocket_handler)

if __name__ == '__main__':
    log_info(f"Starting Server on port {PORT}...")
    web.run_app(app, port=PORT, print=None)
