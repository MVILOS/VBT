# VBT Coach Platform - Frontend

A professional React web application for coaching velocity-based training (VBT) athletes. This is the coach-side dashboard that connects to a FastAPI backend.

## Features

- **Authentication**: Secure login with JWT token management
- **Athlete Management**: Create, edit, and manage athlete profiles
- **Training Plans**: Build custom training plans with exercises and sets
- **Calendar**: Schedule and track training sessions
- **Analytics**: Comprehensive performance analytics including:
  - Velocity trends over time
  - 1RM (one-rep max) progress tracking
  - Training volume analysis
  - Multi-athlete comparison charts

## Tech Stack

- **React 18** with TypeScript
- **Vite** - Fast build tool
- **React Router v6** - Client-side routing
- **Axios** - HTTP client with interceptors
- **FullCalendar** - Training calendar widget
- **Recharts** - Data visualization
- **TailwindCSS** - Responsive styling

## Project Structure

```
src/
├── api/
│   └── client.ts              # Axios instance with auth interceptors
├── components/
│   ├── Layout.tsx             # Main layout with sidebar
│   └── ProtectedRoute.tsx      # Auth guard component
├── context/
│   └── AuthContext.tsx         # Authentication context & hooks
├── pages/
│   ├── LoginPage.tsx           # Login form
│   ├── Dashboard.tsx           # Stats and recent sessions
│   ├── AthletesPage.tsx        # Athlete management
│   ├── PlansPage.tsx           # Training plan builder
│   ├── CalendarPage.tsx        # Training calendar
│   └── AnalyticsPage.tsx       # Performance analytics
├── types/
│   └── index.ts                # TypeScript interfaces
├── App.tsx                      # Routes and setup
├── main.tsx                     # Entry point
└── index.css                    # Global styles
```

## Setup & Installation

### Prerequisites

- Node.js 18+
- npm or yarn

### Development

1. Install dependencies:
```bash
npm install
```

2. Start dev server:
```bash
npm run dev
```

The app will be available at `http://localhost:5173` with the API proxy at `/api` → `http://localhost:8000`

3. Build for production:
```bash
npm run build
```

4. Preview production build:
```bash
npm run preview
```

## Environment Variables

The app uses the Vite dev server proxy to forward API calls to `http://localhost:8000`. Ensure your FastAPI backend is running on that port.

## API Integration

The frontend communicates with the FastAPI backend at `/api` endpoints:

**Auth**
- `POST /api/auth/login` - User login
- `GET /api/auth/me` - Current user info

**Users**
- `GET /api/users/athletes` - List athletes
- `POST /api/users/athletes` - Create athlete
- `PUT /api/users/athletes/{id}` - Update athlete
- `DELETE /api/users/athletes/{id}` - Delete athlete

**Training Plans**
- `GET /api/plans` - List plans
- `POST /api/plans` - Create plan
- `PUT /api/plans/{id}` - Update plan
- `DELETE /api/plans/{id}` - Delete plan

**Exercises**
- `GET /api/exercises` - List all exercises

**Calendar**
- `GET /api/calendar` - List calendar entries
- `POST /api/calendar` - Create entry
- `PUT /api/calendar/{id}` - Update entry
- `DELETE /api/calendar/{id}` - Delete entry

**Dashboard**
- `GET /api/dashboard/stats` - Dashboard statistics
- `GET /api/dashboard/recent-sessions` - Recent sessions

**Analytics**
- `GET /api/analytics/velocity-trend` - Velocity data
- `GET /api/analytics/1rm-progress` - 1RM progress
- `GET /api/analytics/volume` - Training volume
- `GET /api/analytics/compare-athletes` - Athlete comparison

## Authentication Flow

1. User enters credentials on login page
2. Frontend sends `POST /api/auth/login`
3. Backend returns JWT token + user data
4. Token stored in localStorage
5. All subsequent requests include `Authorization: Bearer <token>` header
6. On 401 response, user is redirected to login

## Styling

The application uses TailwindCSS with a dark theme:

- **Primary colors**: `bg-gray-900` (dark), `bg-gray-800` (lighter)
- **Accent color**: `violet-600` (primary actions)
- **Typography**: Light gray text on dark backgrounds
- **Borders**: `border-gray-700`

Custom Tailwind config extends with a `accent` color palette.

## Docker Deployment

Build and run the container:

```bash
# Build
docker build -t vbt-coach-frontend .

# Run
docker run -p 80:80 vbt-coach-frontend
```

The Dockerfile uses multi-stage build to:
1. Build React app with Vite
2. Serve with nginx on port 80
3. Configure SPA routing with `try_files`

## Key Components

### AuthContext
Manages user authentication state and provides login/logout functions.

```typescript
const { user, token, login, logout, isLoading } = useAuth()
```

### Layout
Main app wrapper with sidebar navigation and header.

### ProtectedRoute
Guards routes requiring authentication.

### Pages
- **Dashboard**: Stats overview and recent activity
- **Athletes**: CRUD operations on athlete profiles
- **Plans**: Build training plans with exercises and sets
- **Calendar**: Visual calendar for scheduling sessions
- **Analytics**: Performance charts and trends

## Notes

- All dates are formatted with `date-fns`
- API responses are intercepted for automatic token refresh on 401
- Modal components are managed with local state
- Charts use Recharts with custom dark theme styling
- FullCalendar colors are determined by athlete ID hash
