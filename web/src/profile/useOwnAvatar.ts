import { useMutation, useQueryClient } from '@tanstack/react-query'
import { removeOwnAvatar, setOwnAvatar } from '../api/profile'
import { currentUserKey, loginUsersKey } from '../auth/useCurrentUser'
import { listKeys, membersKey } from '../lists/queries'

/**
 * Uploading or removing the caller's own picture.
 *
 * A face appears in the shell, the sign-in roster, the member pickers and
 * every list's audience, so the simplest correct thing is to invalidate all of
 * them rather than keep a list of places a face can appear.
 */
export function useOwnAvatar() {
  const queryClient = useQueryClient()

  function invalidateEverywhereAFaceAppears() {
    void queryClient.invalidateQueries({ queryKey: currentUserKey })
    void queryClient.invalidateQueries({ queryKey: loginUsersKey })
    void queryClient.invalidateQueries({ queryKey: membersKey })
    void queryClient.invalidateQueries({ queryKey: listKeys.all })
  }

  const upload = useMutation({
    mutationFn: (file: File) => setOwnAvatar(file),
    onSuccess: invalidateEverywhereAFaceAppears,
  })

  const remove = useMutation({
    mutationFn: () => removeOwnAvatar(),
    onSuccess: invalidateEverywhereAFaceAppears,
  })

  return {
    upload: upload.mutate,
    remove: remove.mutate,
    isPending: upload.isPending || remove.isPending,
    error: upload.error ?? remove.error,
  }
}
