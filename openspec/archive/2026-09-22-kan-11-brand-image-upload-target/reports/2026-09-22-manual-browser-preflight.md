# Manual browser preflight check — Brand image upload target (2026-09-22)

Change: `kan-11-brand-image-upload-target` (branch `feat/kan-11-brand-image-upload-target`).

AC14 cannot be proven by the automated suite: LocalStack does not enforce CORS and
MockMvc/`HttpClient` are not browsers. This report records the mandatory manual browser check
(DoD item; missing check = incomplete DoD).

## Method

- Real browser: `google-chrome --headless=new` (desktop Chrome, Blink engine — issues a genuine
  CORS preflight for cross-origin non-simple requests).
- App origin simulated by a static page served from `http://localhost:3000/check.html`
  (`python3 -m http.server 3000`), i.e. a different origin than both the API (`:8080`) and the
  object store (`:4566`), exactly like the production browser caller.
- The page performs the real client flow against a freshly issued target
  (`POST /api/brands/1/images` → `uploadUrl`, TTL 15 min):

```js
fetch(uploadUrl, {
  method: "PUT",
  body: "browser-bytes-kan-11",
  headers: {"content-type": "application/octet-stream"}
}).then(r => { document.title = "STATUS:" + r.status; })
 .catch(e => { document.title = "ERROR:" + e; });
```

`PUT` is never a CORS-simple method and `application/octet-stream` is not a safelisted content
type, so Chrome is forced to issue the preflight `OPTIONS` first; only if the bucket answers with
`Access-Control-Allow-Origin` / `Access-Control-Allow-Methods: PUT` does the `PUT` proceed.

- Bucket CORS at verification time (mirrors `localstack-resources.yml` after this change):

```json
{"CORSRules": [{"AllowedHeaders": ["content-type"], "AllowedMethods": ["PUT"],
 "AllowedOrigins": ["*"], "ExposeHeaders": ["Date"], "MaxAgeSeconds": 3000}]}
```

(`*` origin is local-only; production must list explicit app origins.)

## Result

```
$ google-chrome --headless=new --no-sandbox --disable-gpu \
    --virtual-time-budget=15000 --dump-dom http://localhost:3000/check.html \
    | grep -o "<title>[^<]*</title>"
<title>STATUS:200</title>
```

PASS (AC14): the preflight succeeded and the browser `PUT` stored the object (`200`). A missing
or wrong bucket CORS rule would have failed here with an `ERROR:TypeError: Failed to fetch`
while the entire JUnit suite stayed green — the exact "green build, broken product" failure this
check exists to prevent.

## Follow-ups (unchanged by this result)

- Production bucket rule rides the infrastructure track (KAN-14 / KAN-12 F7) with explicit app
  origins — this check proves the mechanism, not the production values.
- `content-type` is already in `AllowedHeaders`, so the F12 declared-MIME story needs no CORS
  change.
