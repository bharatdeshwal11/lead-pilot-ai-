import { randomUUID } from 'node:crypto'
import { expect, test, type Locator, type Page } from '@playwright/test'
import { overdueFollowUps, signInAsDemoAdmin, totalLeads } from './demo'

test('admin manages a lead from enquiry to completed follow-up', async ({ page }, testInfo) => {
  const name = `Browser Demo ${randomUUID().slice(0, 8)}`
  const email = `${name.toLowerCase().replaceAll(' ', '-')}@example.test`
  const enquiry = 'I am looking for a 3BHK apartment in Greater Noida. My budget is 90 lakh and I need to move within two months. Can we arrange a site visit?'
  const reply = 'Thank you for sharing your requirements. Please share a convenient time to discuss a site visit.'
  const note = `Confirm preferred visit dates for ${name}`
  let leadId: number | undefined

  try {
    await page.goto('/login')
    await signInAsDemoAdmin(page)
    const originalTotal = await readCount(totalLeads(page))
    const originalOverdue = await readCount(overdueFollowUps(page))
    const navigation = page.getByRole('navigation', { name: 'Main navigation' })

    await test.step('Create and view an assigned lead', async () => {
      await page.getByRole('link', { name: 'New lead', exact: true }).click()
      await expect(page.getByRole('heading', { name: 'Create lead' })).toBeVisible()
      await page.getByLabel('Customer name').fill(name)
      await page.getByLabel('Email address').fill(email)
      await page.getByLabel('Source').selectOption('WEBSITE')
      await page.getByLabel('Enquiry message').fill(enquiry)
      await page.getByLabel('Assign to').selectOption({ index: 1 })
      const [created] = await Promise.all([
        page.waitForResponse(response => new URL(response.url()).pathname === '/api/leads'
          && response.request().method() === 'POST'),
        page.getByRole('button', { name: 'Create Lead' }).click(),
      ])
      expect(created.status()).toBe(201)
      const lead = (await created.json()) as { id: number }
      leadId = lead.id
      await expect(page).toHaveURL(`/leads/${leadId}`)
      await expect(page.getByRole('heading', { name })).toBeVisible()
      await expect(page.getByText(enquiry, { exact: true })).toBeVisible()
    })

    const sections = page.getByRole('navigation', { name: 'Lead sections' })
    await test.step('Analyze the enquiry and save a recommended priority', async () => {
      await page.getByRole('button', { name: 'Analyze Lead' }).click()
      await expect(page.getByRole('heading', { name: 'Suggested next action' })).toBeVisible()
      await expect(page.getByText('Stated budget', { exact: true }).locator('..').locator('dd')).toContainText(/90\s*lakh/i)
      await page.getByRole('button', { name: 'Apply recommended priority' }).click()
      await expect(page.getByRole('button', { name: 'Apply recommended priority' })).toHaveCount(0)
    })

    await test.step('Generate, edit, submit, and approve a reply', async () => {
      await sections.getByRole('link', { name: 'Reply Drafts' }).click()
      await page.getByRole('button', { name: 'Generate Draft' }).click()
      const body = page.getByLabel('Reply body')
      await expect(body).toHaveValue(/\S/)
      await body.fill(reply)
      await page.getByRole('button', { name: 'Save Draft' }).click()
      await expect(page.getByRole('button', { name: 'Save Draft' })).toBeDisabled()
      await page.getByRole('button', { name: 'Submit for Review' }).click()
      await page.getByRole('button', { name: 'Approve Draft' }).click()
      await expect(page.getByText('Approved', { exact: true })).toBeVisible()
      await page.reload()
      await expect(page.getByText('Approved', { exact: true })).toBeVisible()
      await expect(body).toHaveValue(reply)
      await expect(body).not.toBeEditable()
    })

    await test.step('Schedule an overdue task and verify the database-backed overview', async () => {
      await sections.getByRole('link', { name: 'Follow-ups' }).click()
      await page.getByRole('button', { name: 'Schedule Follow-up' }).click()
      const yesterday = Math.floor((Date.now() - 24 * 60 * 60 * 1000) / 60_000) * 60_000
      const localDueDate = await page.evaluate(instant => {
        const offset = new Date(instant).getTimezoneOffset() * 60_000
        return new Date(instant - offset).toISOString().slice(0, 16)
      }, yesterday)
      await page.getByLabel('Due date and time').fill(localDueDate)
      await page.getByLabel('Follow-up note').fill(note)
      await page.getByRole('button', { name: 'Save Follow-up' }).click()
      const task = page.getByRole('article').filter({ hasText: note })
      await expect(task.getByText('Overdue', { exact: true })).toBeVisible()
      expect(Date.parse(await task.locator('time').getAttribute('datetime') ?? '')).toBe(yesterday)

      await navigation.getByRole('link', { name: 'Overview' }).click()
      await expect(totalLeads(page)).toHaveText(formatCount(originalTotal + 1))
      await expect(overdueFollowUps(page)).toHaveText(formatCount(originalOverdue + 1))
    })

    await test.step('Complete the task and see it in lead history', async () => {
      await page.getByRole('link', { name: 'Overdue follow-ups', exact: true }).click()
      const task = page.getByRole('article').filter({ hasText: note })
      await task.getByRole('button', { name: 'Mark complete' }).click()
      await expect(task).toHaveCount(0)
      await navigation.getByRole('link', { name: 'Overview' }).click()
      await expect(overdueFollowUps(page)).toHaveText(formatCount(originalOverdue))
      await page.goto(`/leads/${leadId}?section=follow-ups`)
      await expect(task.getByText('Completed', { exact: true })).toBeVisible()
    })

    await test.step('Confirm deletion and sign out', async () => {
      await sections.getByRole('link', { name: 'Overview' }).click()
      const confirmation = page.waitForEvent('dialog').then(async dialog => {
        await dialog.accept()
        expect(dialog.type()).toBe('confirm')
      })
      await Promise.all([confirmation, page.getByRole('button', { name: 'Delete Lead' }).click()])
      await expect(page).toHaveURL('/leads')
      leadId = undefined
      await navigation.getByRole('link', { name: 'Overview' }).click()
      await expect(totalLeads(page)).toHaveText(formatCount(originalTotal))

      await page.getByRole('button', { name: 'Sign out' }).click()
      await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
      await page.goto('/leads')
      await expect(page).toHaveURL('/login')
    })
  } finally {
    if (leadId !== undefined) {
      try {
        await deleteTestLead(page, leadId)
      } catch {
        testInfo.annotations.push({ type: 'cleanup', description: `Could not remove test lead #${leadId}.` })
      }
    }
  }
})

test('mobile navigation opens and returns to the workspace', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/login')
  await signInAsDemoAdmin(page)
  await expect(totalLeads(page)).toBeVisible()

  const menu = page.getByRole('button', { name: 'Open menu' })
  const navigation = page.getByRole('navigation', { name: 'Main navigation' })
  await menu.click()
  await expect(menu).toHaveAttribute('aria-expanded', 'true')
  await navigation.getByRole('link', { name: 'All leads' }).click()
  await expect(page.getByRole('heading', { name: 'Lead Management' })).toBeVisible()
  await expect(menu).toHaveAttribute('aria-expanded', 'false')
  await menu.click()
  await navigation.getByRole('link', { name: 'Overview' }).click()
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()
})

function formatCount(value: number) {
  return new Intl.NumberFormat('en-IN').format(value)
}

async function readCount(metric: Locator) {
  await expect(metric).toHaveText(/^[\d,]+$/)
  return Number((await metric.innerText()).replaceAll(',', ''))
}

async function deleteTestLead(page: Page, leadId: number) {
  const csrf = await page.request.get('/api/auth/csrf', { failOnStatusCode: true })
  const { token, headerName } = (await csrf.json()) as { token: string; headerName: string }
  await page.request.delete(`/api/leads/${leadId}`, { headers: { [headerName]: token }, failOnStatusCode: true })
}
