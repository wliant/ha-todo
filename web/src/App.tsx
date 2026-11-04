import { Navigate, Route, Routes } from 'react-router-dom'
import { AdminRoute } from './auth/AdminRoute'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AdminPage } from './pages/AdminPage'
import { ListsPage } from './pages/ListsPage'
import { LoginPage } from './pages/LoginPage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/lists"
        element={
          <ProtectedRoute>
            <ListsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <AdminRoute>
            <AdminPage />
          </AdminRoute>
        }
      />
      <Route path="*" element={<Navigate to="/lists" replace />} />
    </Routes>
  )
}
