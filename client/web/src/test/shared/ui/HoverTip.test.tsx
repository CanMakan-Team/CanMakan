import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { HoverTip } from '../../../shared/ui/HoverTip'

describe('HoverTip', () => {
  it('shows the help bubble on hover and hides it when the pointer leaves', async () => {
    const user = userEvent.setup()
    render(
      <HoverTip text="Explains the figure.">
        <span>Unique Products Scanned</span>
      </HoverTip>,
    )

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    await user.hover(screen.getByText('Unique Products Scanned'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Explains the figure.')
    expect(screen.getByText('Unique Products Scanned').closest('.hover-tip')).toBeTruthy()
    await user.unhover(screen.getByText('Unique Products Scanned'))
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })
})
