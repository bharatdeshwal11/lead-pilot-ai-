import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import { signInAsDemoAdmin, totalLeads } from './demo'

test('capture demo screenshots', async ({ page }) => {
  const directory = new URL('../../docs/images/', import.meta.url)
  await mkdir(directory, { recursive: true })
  const imagePath = (name: string) => fileURLToPath(new URL(name, directory))
  await page.emulateMedia({ reducedMotion: 'reduce' })

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await page.screenshot({ path: imagePath('login-desktop.png'), animations: 'disabled' })

  await signInAsDemoAdmin(page)
  await expect(totalLeads(page)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Pipeline by status' })).toBeVisible()
  await page.screenshot({ path: imagePath('dashboard-desktop.png'), animations: 'disabled', fullPage: true })

  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('button', { name: 'Open menu' })).toBeVisible()
  await page.screenshot({ path: imagePath('dashboard-mobile.png'), animations: 'disabled' })
})
