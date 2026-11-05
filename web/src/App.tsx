import { Navigate, Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { AdminRoute } from './auth/AdminRoute'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { AdminPage } from './pages/AdminPage'
import { AllListsPage } from './pages/AllListsPage'
import { CreateListPage } from './pages/CreateListPage'
import { ActivityTab, ItemsTab, ListDetailPage } from './pages/ListDetailPage'
import { ListSettingsPage } from './pages/ListSettingsPage'
import { LoginPage } from './pages/LoginPage'
import { ProfilePage } from './pages/ProfilePage'
import { SharePage } from './pages/SharePage'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Everything signed-in hangs off one layout route, so the sidebar is
          mounted once rather than per screen. */}
      <Route
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      >
        <Route path="/lists" element={<AllListsPage />} />
        <Route path="/lists/new" element={<CreateListPage />} />
        {/* The tabs are routes, so each way of looking at a list has its own
            URL and the back button behaves. Settings stays a sibling: it is a
            separate screen, not another way of looking at the list. */}
        <Route path="/lists/:listId" element={<ListDetailPage />}>
          <Route index element={<ItemsTab />} />
          <Route path="activity" element={<ActivityTab />} />
        </Route>
        <Route path="/lists/:listId/settings" element={<ListSettingsPage />} />
        {/* A share arriving while signed out comes back here afterwards,
            because ProtectedRoute remembers where it was going. */}
        <Route path="/share" element={<SharePage />} />
        <Route path="/profile" element={<ProfilePage />} />
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
