import { test, expect } from '@playwright/test'

const publisherPassword = process.env.E2E_USER_A_PASSWORD
const recipientPassword = process.env.E2E_USER_B_PASSWORD

async function login(page, username, password, redirect = '/') {
  await page.goto(`/login?redirect=${encodeURIComponent(redirect)}`)
  await page.getByLabel('账号').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(new RegExp(`${redirect.replaceAll('/', '\\/')}$`))
}

test('publisher and recipient complete the handover workflow', async ({ browser }) => {
  test.skip(!publisherPassword || !recipientPassword, 'E2E demo passwords must be provided')
  const title = `E2E 台灯 ${Date.now()}`
  const start = new Date(Date.now() + 48 * 60 * 60 * 1000)
  const end = new Date(start.getTime() + 60 * 60 * 1000)
  const localInput = (value) => {
    const shifted = new Date(value.getTime() - value.getTimezoneOffset() * 60_000)
    return shifted.toISOString().slice(0, 16)
  }

  const publisherContext = await browser.newContext()
  const publisher = await publisherContext.newPage()
  await login(publisher, 'neighbor_a', publisherPassword)
  await publisher.goto('/publish')
  await publisher.getByLabel('物品名称').fill(title)
  await publisher.getByLabel('物品描述').fill('Playwright 隔离环境自动化测试物品')
  await publisher.getByLabel('公共交接地点').fill('社区服务中心门口')
  const times = publisher.locator('input[type="datetime-local"]')
  await times.nth(0).fill(localInput(start))
  await times.nth(1).fill(localInput(end))
  await publisher.getByRole('button', { name: '确认发布' }).click()
  await expect(publisher).toHaveURL(/\/items\/\d+$/)
  await expect(publisher.getByRole('heading', { name: title })).toBeVisible()
  const itemPath = new URL(publisher.url()).pathname

  const recipientContext = await browser.newContext()
  const recipient = await recipientContext.newPage()
  await login(recipient, 'neighbor_b', recipientPassword, itemPath)
  await recipient.locator('input[type="radio"]').check()
  await recipient.getByRole('button', { name: '我想要' }).click()
  await expect(recipient.getByText('已提交意向，等待发布者确认。')).toBeVisible()

  await publisher.reload()
  await publisher.getByRole('button', { name: '确认此人' }).click()
  await expect(publisher.getByText('已确认领取人，物品进入待交接。')).toBeVisible()

  await publisher.goto('/mine')
  const handoverCard = publisher.locator('.handover-card').filter({ hasText: title })
  await expect(handoverCard).toBeVisible()
  await handoverCard.getByRole('button', { name: '标记已送出' }).click()
  await expect(publisher.getByText('交接已完成并归档。')).toBeVisible()
  await expect(publisher.getByText('已送出').first()).toBeVisible()

  await recipientContext.close()
  await publisherContext.close()
})
