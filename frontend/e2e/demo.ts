import { expect, type Page } from '@playwright/test'

export function requiredEnvironment(name: string) {
  const value = process.env[name]
  if (!value) throw new Error(`Set ${name} using the credentials for your local demo stack.`)
  return value
}

export async function signInAsDemoAdmin(page: Page) {
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  const password = page.getByLabel('Password', { exact: true })
  await page.getByLabel('Email address', { exact: true }).fill(requiredEnvironment('APP_DEMO_ADMIN_EMAIL'))
  await password.fill(requiredEnvironment('APP_DEMO_ADMIN_PASSWORD'))
  try {
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Overview', exact: true })).toBeVisible()
  } catch (error) {
    if (await password.count()) await password.clear({ timeout: 1000 }).catch(() => undefined)
    throw error
  }
}

export function totalLeads(page: Page) {
  return page.getByRole('article').filter({ has: page.getByText('Total leads', { exact: true }) }).locator('strong')
}

export function overdueFollowUps(page: Page) {
  return page.getByRole('listitem').filter({ has: page.getByRole('link', { name: 'Overdue follow-ups', exact: true }) })
    .locator('strong')
}
