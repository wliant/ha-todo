import { useState } from 'react'
import Box from '@mui/material/Box'
import FormHelperText from '@mui/material/FormHelperText'
import IconButton from '@mui/material/IconButton'
import Stack from '@mui/material/Stack'
import ToggleButton from '@mui/material/ToggleButton'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import FormatBoldRounded from '@mui/icons-material/FormatBoldRounded'
import FormatItalicRounded from '@mui/icons-material/FormatItalicRounded'
import FormatListBulletedRounded from '@mui/icons-material/FormatListBulletedRounded'
import FormatListNumberedRounded from '@mui/icons-material/FormatListNumberedRounded'
import ImageRounded from '@mui/icons-material/ImageRounded'
import { EditorContent, useEditor } from '@tiptap/react'
import Image from '@tiptap/extension-image'
import StarterKit from '@tiptap/starter-kit'
import { uploadDetailImage } from '../api/profile'

const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp']
const MAX_BYTES = 5 * 1024 * 1024

/**
 * A rich-text body: steps, notes, and pictures.
 *
 * <p>Used for the detail field (R21) and for a completion note (R37). The label
 * is a prop because two of these can be on screen at once, and two controls
 * both called "Detail" are indistinguishable to a screen reader and to a test.
 *
 * <p>Pictures are uploaded and referenced by URL, never inlined as base64 -
 * which would put megabytes into a column every item read returns. If that
 * interception were ever bypassed the sanitizer drops the image on the server,
 * so a bypass loses a picture rather than storing one (adr/0018).
 */
export function DetailEditor({
  value,
  required,
  onChange,
  label = 'Detail',
  helperText = 'Steps, notes, and pictures. Paste or drop an image straight in.',
}: {
  value: string
  required: boolean
  onChange: (html: string) => void
  label?: string
  helperText?: string
}) {
  const [problem, setProblem] = useState<string | null>(null)

  const editor = useEditor({
    extensions: [
      StarterKit.configure({ heading: { levels: [2, 3] } }),
      Image.configure({ inline: false }),
    ],
    content: value,
    onUpdate: ({ editor: current }) => onChange(current.getHTML()),
    editorProps: {
      attributes: { 'aria-label': label, role: 'textbox' },
      handlePaste: (_view, event) => insertFiles(event.clipboardData?.files),
      handleDrop: (_view, event) => insertFiles((event as DragEvent).dataTransfer?.files),
    },
  })

  function insertFiles(files: FileList | null | undefined): boolean {
    const images = Array.from(files ?? []).filter((file) => ACCEPTED.includes(file.type))
    if (images.length === 0) {
      return false
    }
    images.forEach((file) => void attach(file))
    // Handled: the default would have inlined it as a data: URL.
    return true
  }

  async function attach(file: File) {
    setProblem(null)
    if (!ACCEPTED.includes(file.type)) {
      setProblem('Pictures must be a PNG, JPEG or WebP.')
      return
    }
    if (file.size > MAX_BYTES) {
      setProblem('That picture is too large - 5 MB at most.')
      return
    }
    try {
      const url = await uploadDetailImage(file)
      if (!editor) {
        return
      }
      // The selection is collapsed to its end first, then the picture is
      // inserted there. Without that, inserting replaces whatever is
      // selected - so a member who selects their steps and adds a photograph
      // loses the steps.
      const at = editor.state.selection.to
      editor
        .chain()
        .focus()
        .setTextSelection(at)
        .insertContent({ type: 'image', attrs: { src: url } })
        .run()
    } catch {
      setProblem('Could not add that picture.')
    }
  }

  if (!editor) {
    return null
  }

  return (
    <Box>
      <Typography variant="caption" color="text.secondary" component="div" sx={{ mb: 0.5 }}>
        {label}
        {required ? ' (required)' : ''}
      </Typography>

      <Stack direction="row" spacing={0.5} sx={{ mb: 0.5, flexWrap: 'wrap' }}>
        <Mark editor={editor} name="bold" label="Bold">
          <FormatBoldRounded fontSize="small" />
        </Mark>
        <Mark editor={editor} name="italic" label="Italic">
          <FormatItalicRounded fontSize="small" />
        </Mark>
        <Mark editor={editor} name="bulletList" label="Bulleted list">
          <FormatListBulletedRounded fontSize="small" />
        </Mark>
        <Mark editor={editor} name="orderedList" label="Numbered steps">
          <FormatListNumberedRounded fontSize="small" />
        </Mark>
        <Tooltip title="Add a picture">
          <IconButton size="small" component="label" aria-label="Add a picture">
            <ImageRounded fontSize="small" />
            <input
              type="file"
              accept={ACCEPTED.join(',')}
              hidden
              onChange={(event) => {
                const file = event.target.files?.[0]
                if (file) void attach(file)
                event.target.value = ''
              }}
            />
          </IconButton>
        </Tooltip>
      </Stack>

      <Box
        sx={{
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
          p: 1.5,
          minHeight: '8rem',
          '& .ProseMirror': { outline: 'none', minHeight: '6rem' },
          '& .ProseMirror img': { maxWidth: '100%', borderRadius: 1 },
          '& .ProseMirror p': { m: 0, mb: 1 },
          '& .ProseMirror ul, & .ProseMirror ol': { pl: 3, m: 0, mb: 1 },
        }}
      >
        <EditorContent editor={editor} />
      </Box>

      <FormHelperText error={problem !== null}>
        {problem ?? helperText}
      </FormHelperText>
    </Box>
  )
}

function Mark({
  editor,
  name,
  label,
  children,
}: {
  editor: NonNullable<ReturnType<typeof useEditor>>
  name: 'bold' | 'italic' | 'bulletList' | 'orderedList'
  label: string
  children: React.ReactNode
}) {
  const commands = {
    bold: () => editor.chain().focus().toggleBold().run(),
    italic: () => editor.chain().focus().toggleItalic().run(),
    bulletList: () => editor.chain().focus().toggleBulletList().run(),
    orderedList: () => editor.chain().focus().toggleOrderedList().run(),
  }
  return (
    <Tooltip title={label}>
      <ToggleButton
        value={name}
        size="small"
        selected={editor.isActive(name)}
        onChange={commands[name]}
        aria-label={label}
        sx={{ border: 0, borderRadius: 1 }}
      >
        {children}
      </ToggleButton>
    </Tooltip>
  )
}
