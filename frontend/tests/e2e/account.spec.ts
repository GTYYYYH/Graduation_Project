import { test, expect } from '@playwright/test'
import { readFile, realpath, stat } from 'node:fs/promises'
import path from 'node:path'

const project = path.resolve(process.cwd(), '..')
const control = path.join(project, 'tests/evidence/B11-browser-control.json')

test('isolated reporter sees real campus catalog and own applications', async ({ page }) => {
  const fixture = JSON.parse(await readFile(control, 'utf8'))
  if (fixture.profile !== 'test' || fixture.database !== 'campus_maintenance_test' || fixture.backendPort !== 8081)
    throw new Error('Refusing non-test fixture')
  const parent = await realpath(path.join(project, 'data/attachments/test'))
  const fixtureDir = await realpath(fixture.controlDirectory)
  if (path.dirname(fixtureDir) !== parent || !/^b11-browser-[0-9a-f-]{36}$/.test(path.basename(fixtureDir)))
    throw new Error('Refusing unexpected fixture directory')
  await stat(path.join(fixtureDir, 'ready'))
  if (fixture.accounts.reporter == null) throw new Error('Missing test reporter')
  await page.goto('/login')
  await page.getByLabel('登录账号').fill('b11_ui_reporter')
  await page.getByLabel('密码').fill('Synthetic1!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '校园总览' })).toBeVisible()
  await expect(page.getByText('B11原报修人 · 报修用户')).toBeVisible()
  await expect(page.getByLabel('楼栋')).toContainText('文博3')
  await page.getByRole('link', { name: '我的申请' }).click()
  await expect(page.getByRole('heading', { name: '我的申请' })).toBeVisible()
  await expect(page.getByText('B11浏览器文博3同号')).toBeVisible()
  await page.getByRole('button', { name: '退出登录' }).click()
  await expect(page.getByRole('heading', { name: '登录你的账号' })).toBeVisible()
})
