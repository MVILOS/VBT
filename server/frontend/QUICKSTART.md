# VBT Coach Frontend - Quick Start Guide

## Installation & Development

### 1. Install Dependencies
```bash
cd /home/diabolik_zotac/Documents/vbt/server/frontend
npm install
```

### 2. Start Development Server
```bash
npm run dev
```

Open `http://localhost:5173` in your browser.

**Important:** Ensure your FastAPI backend is running on `http://localhost:8000` for the API proxy to work.

### 3. Build for Production
```bash
npm run build
```

Creates optimized bundle in `dist/` directory.

## Project Structure Overview

```
frontend/
├── src/
│   ├── pages/           # Page components (6 feature pages)
│   ├── components/      # Reusable components (Layout, ProtectedRoute)
│   ├── context/         # Auth context & state management
│   ├── api/             # Axios HTTP client
│   ├── types/           # TypeScript interfaces
│   ├── App.tsx          # Router setup
│   ├── main.tsx         # Entry point
│   └── index.css        # Global styles
├── index.html           # HTML template
├── package.json         # Dependencies
├── vite.config.ts       # Vite config with /api proxy
├── tsconfig.json        # TypeScript config
├── tailwind.config.js   # TailwindCSS config
├── Dockerfile           # Docker build config
└── README.md            # Full documentation
```

## Key Features

### Pages
1. **Login** - JWT authentication
2. **Dashboard** - Stats & recent activity
3. **Athletes** - Manage athlete profiles (CRUD)
4. **Plans** - Build training plans with exercises/sets
5. **Calendar** - Schedule and track sessions
6. **Analytics** - Performance charts & trends

### Authentication
- Login required to access all pages except `/login`
- JWT token stored in localStorage
- Auto-refresh on page reload
- Auto-logout on 401 response

### API Integration
All requests automatically:
- Include `Authorization: Bearer <token>` header
- Proxy through `/api` to backend
- Handle 401 responses with logout

## Common Tasks

### Add a New Page
1. Create file in `src/pages/NewPage.tsx`
2. Import in `src/App.tsx`
3. Add route with `<ProtectedRoute><Layout><NewPage /></Layout></ProtectedRoute>`

### Modify Navigation
Edit sidebar items in `src/components/Layout.tsx` → `menuItems` array

### Change Styling
- Dark theme colors in `src/index.css`
- TailwindCSS custom colors in `tailwind.config.js`
- Component styles use TailwindCSS classes

### Connect New API Endpoint
1. Define types in `src/types/index.ts` if needed
2. Use `client.get/post/put/delete('/path')` in page component
3. Import `client` from `src/api/client`

## Troubleshooting

### API calls fail with CORS errors
- Ensure backend is running on `http://localhost:8000`
- Check `vite.config.ts` proxy settings

### Login doesn't work
- Verify backend endpoint: `POST /api/auth/login`
- Check network tab in browser DevTools
- Verify credentials are correct

### Styling looks wrong
- Run `npm run build` to rebuild CSS
- Clear browser cache (Ctrl+Shift+Delete)
- Check TailwindCSS classes are applied

### TypeScript errors
- Run `npm install` to ensure dependencies
- Check `tsconfig.json` paths
- Verify all imports are correct

## Environment Variables

No `.env` file needed for development. API proxy is configured in `vite.config.ts`.

To change API URL in production, update the proxy target in `vite.config.ts`.

## Docker Deployment

```bash
# Build image
docker build -t vbt-coach-frontend .

# Run container
docker run -p 80:80 vbt-coach-frontend
```

App will be available at `http://localhost`

## Performance Tips

- Lazy load page components with React.lazy() if needed
- Use React DevTools profiler to find slow renders
- Bundle size: Check with `npm run build` output

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Requires JavaScript enabled

## Resources

- React docs: https://react.dev
- Vite docs: https://vitejs.dev
- TailwindCSS: https://tailwindcss.com
- TypeScript: https://www.typescriptlang.org

## Support

Check `README.md` for detailed documentation on all features and API endpoints.
