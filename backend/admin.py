import os
import sys
import requests
import json
from dotenv import load_dotenv

load_dotenv()

try:
    PORT = os.environ["PORT"]
except KeyError:
    print("Error: Missing PORT in .env")
    sys.exit(1)

if not os.path.exists(".admin_session"):
    print("Error: Server is not running or .admin_session is missing!")
    sys.exit(1)

with open(".admin_session", "r") as f:
    TOKEN = f.read().strip()

URL = f"http://127.0.0.1:{PORT}/api/admin/broadcast"

if len(sys.argv) < 3:
    print("Usage:")
    print("  python admin.py toast \"Title\" \"Description\"")
    print("  python admin.py chat \"Chat message\"")
    sys.exit(1)

cmd = sys.argv[1].lower()
headers = {"Authorization": f"Bearer {TOKEN}"}

if cmd == "toast":
    title = sys.argv[2]
    desc = sys.argv[3] if len(sys.argv) > 3 else ""
    data = {"type": "toast", "toast_type": 1, "title": title, "desc": desc}
elif cmd == "chat":
    msg_json = json.dumps({"text": sys.argv[2], "color": "green"})
    data = {"type": "chat", "message": msg_json}
else:
    print("Unknown command. Use 'toast' or 'chat'.")
    sys.exit(1)

try:
    resp = requests.post(URL, json=data, headers=headers)
    print("Success:", resp.json())
except Exception as e:
    print("Connection error:", e)
