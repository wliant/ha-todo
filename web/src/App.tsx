import { Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './app/AppLayout'
import { AdminRoute } from './auth/AdminRoute'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AdminPage } from './pages/AdminPage'
import { ListsPage } from './pages/ListsPage'
import { LoginPage } from './pages/LoginPage'
import { SharePage } from './pages/SharePage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Everything signed-in hangs off one layout route, so the navigation
          bar is mounted once rather than per screen. */}
      <Route
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route path="/lists" element={<ListsPage />} />
        {/* A share arriving while signed out comes back here afterwards,
            because ProtectedRoute remembers where it was going. */}
        <Route path="/share" element={<SharePage />} />
        <Route
          path="/admin"
          element={
            <AdminRoute>
              <AdminPage />
            </AdminRoute>
          }
        />
      </Route>

      <Route path="*" element={<Navigate to="/lists" replace />} />
    </Routes>
  )
}
