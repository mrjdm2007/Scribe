# Scribe Web

**A considered space for academic thinking.**

Scribe is being converted from its Android-first implementation into a responsive, installable web application for desktop, tablet and mobile browsers.

## Web app foundation
- Responsive graphite-and-champagne workspace with mobile navigation.
- Course → Chapter → Topic → Page hierarchy.
- Local-first note editing with autosave in the browser.
- Search across note titles, content and tags; bookmarks and quick access.
- PDF and image uploads stored in browser IndexedDB, with downloads.
- JSON backup and restore, including attachment bytes.
- Study dashboard and focus timer.
- Flashcard creation and review-set management foundation.
- Engineering unit conversion and formula pad foundation.
- PWA manifest, service worker and GitHub Actions build/deployment workflow.

## Data and privacy
The initial web build stores notes in browser local storage and attachments in IndexedDB. Data remains on the current browser/device until the user exports a backup. Cross-device sync, authentication and cloud storage are not enabled in this first web foundation.

The planned cloud architecture is Firebase Authentication, Firestore, Cloud Storage and server-side AI integration. Cloud sync must be configured and tested before it is described as available. Never place provider API keys in browser code.

## Deployment
The GitHub Actions workflow builds the Vite app and publishes the dist directory to GitHub Pages. In repository settings, enable Settings → Pages → Build and deployment → GitHub Actions.

The Vite build uses relative asset paths so it can work at the GitHub Pages project URL and on a configured custom domain.

## Custom domain
A custom domain must be registered and controlled by the project owner. Add the verified domain to GitHub Pages settings and configure the DNS records required by GitHub Pages. Do not publish a CNAME file or claim a domain is connected until ownership and DNS are confirmed.

## Migration status
This repository still contains the original Android implementation as a reference while web feature parity is developed. The web app is a separate Vite/React application in src/. Android builds are disabled for the web migration; Android source is not the deployed product.

## Local development
Requires Node.js 22 or newer. Run npm install, npm run typecheck, npm run build, and npm run dev.

## Remaining migration work
The initial foundation is not full feature parity. Remaining work includes rich-text/equation rendering, full PDF reader and OCR, complete question-bank quizzes, robust spaced repetition scheduling, tasks/reminders, version history and trash management, full engineering tools, secure AI assistant backend, and Firebase sign-in/sync with conflict handling and attachment sync.
