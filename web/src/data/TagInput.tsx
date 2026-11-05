import { useState } from 'react'
import Autocomplete from '@mui/material/Autocomplete'
import Chip from '@mui/material/Chip'
import TextField from '@mui/material/TextField'
import type { TagSuggestion } from '../api/dataLists'

/**
 * Free-form tags, offered from what the collection already uses (R52).
 *
 * The suggestions are the whole point: a household converges on its own words,
 * and a list that already says "italian" is how the next member avoids typing
 * "Italian". The server normalises anyway - lowercased, trimmed, deduped - so
 * this only has to make the existing ones easy to pick.
 */
export function TagInput({
  value,
  suggestions,
  onChange,
}: {
  value: string[]
  suggestions: TagSuggestion[]
  onChange: (tags: string[]) => void
}) {
  const [typed, setTyped] = useState('')

  return (
    <Autocomplete
      multiple
      freeSolo
      options={suggestions.map((suggestion) => suggestion.tag)}
      value={value}
      inputValue={typed}
      onInputChange={(_event, next) => setTyped(next)}
      // Normalised here too, so the chip a member sees matches the chip that
      // comes back from the server rather than flickering on save.
      onChange={(_event, next) =>
        onChange(
          Array.from(
            new Set(
              next
                .map((tag) => tag.trim().toLowerCase().replace(/\s+/g, ' '))
                .filter((tag) => tag.length > 0),
            ),
          ).sort(),
        )
      }
      // renderValue, not renderTags: MUI 9 renamed it.
      renderValue={(tags, getItemProps) =>
        tags.map((tag, index) => {
          const { key, ...rest } = getItemProps({ index })
          return <Chip key={key} size="small" label={tag} {...rest} />
        })
      }
      renderInput={(params) => (
        <TextField
          {...params}
          label="Tags"
          placeholder="Add a tag"
          helperText="What you would search for later. Press Enter after each one."
        />
      )}
    />
  )
}
