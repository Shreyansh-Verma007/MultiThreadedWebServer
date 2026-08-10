# 📄 `index.html` — Default Landing Page

**Path:** `src/main/resources/index.html`  
**Role:** The default HTML page served when a user visits the root URL (`/`) of the web server.

---

## File Overview

This is a simple, single-page HTML file that serves as the **default landing page** for the web server. When a user navigates to `http://localhost:8080/`, the `RequestProcessor` rewrites the path to `/index.html` and the `StaticFileHandler` serves this file.

It features a dark-themed, monospace-styled page confirming the server is running.

---

## Line-by-Line Explanation

```html
<!DOCTYPE html>                                                <!-- Line 1: HTML5 document type declaration -->
<html lang="en">                                               <!-- Line 2: Root element, English language -->
<head>                                                         <!-- Line 3: Document metadata section -->
    <title>My Java Web Server</title>                          <!-- Line 4: Browser tab title -->
    <style>                                                    <!-- Line 5: Inline CSS styles -->
        body {
            font-family: monospace;                            /* Monospace font for a terminal/hacker aesthetic */
            text-align: center;                                /* Center all text horizontally */
            margin-top: 100px;                                 /* Push content 100px from the top */
            background-color: #121212;                         /* Dark background (near-black) */
            color: #00ffcc;                                    /* Cyan/teal text color */
        }
    </style>                                                   <!-- Line 7: End of styles -->
</head>                                                        <!-- Line 8: End of head -->
<body>                                                         <!-- Line 9: Visible content section -->
<h1>🚀 The Custom Web Server is ALIVE!</h1>                    <!-- Line 10: Main heading with rocket emoji -->
<p>Served directly from the LRU Cache.</p>                     <!-- Line 11: Subtitle indicating cache serving -->
</body>                                                        <!-- Line 12: End of body -->
</html>                                                        <!-- Line 13: End of document -->
```

---

## Styling Details

| Property | Value | Effect |
|----------|-------|--------|
| `font-family` | `monospace` | Terminal-like appearance |
| `text-align` | `center` | Content horizontally centered |
| `margin-top` | `100px` | Vertical spacing from top |
| `background-color` | `#121212` | Dark/black background |
| `color` | `#00ffcc` | Bright cyan/teal text |

---

## How It's Served

1. Client requests `GET /`
2. `RequestProcessor` rewrites path: `/` → `/index.html`
3. `StaticFileHandler.get("/index.html")`:
   - **First request**: Reads from disk (`src/main/resources/index.html`) or JAR classpath → caches in LRU cache
   - **Subsequent requests**: Served directly from LRU cache (hence "Served directly from the LRU Cache")
4. Response sent with `Content-Type: text/html`

---

## Other Static Resources

The `src/main/resources/` directory also contains:
- `pc.jpg` — Static image (accessible at `/pc.jpg`)
- `tech.jpg` — Static image (accessible at `/tech.jpg`)

These are served the same way through the `StaticFileHandler`.
