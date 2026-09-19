// Runtime API origin. Empty string = same-origin (Traefik routes /api to the backend).
// Overwritten at deploy time by deploy/web.sh when the API lives on another host.
window.__API_BASE__ = "";
