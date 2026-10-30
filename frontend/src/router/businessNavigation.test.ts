import { expect, it } from 'vitest'
import { businessReturnTo, relatedBusinessTo } from './businessNavigation'
it('retains business list filters and detail context while rejecting external or unrelated paths', () => {
  expect(businessReturnTo('/requests?page=3&status=APPROVED', '/requests')).toBe('/requests?page=3&status=APPROVED')
  expect(businessReturnTo('/work-orders/71?returnTo=%2Ftasks%3Fpage%3D2', '/equipment')).toContain('/work-orders/71')
  for (const path of ['//evil.example', '/equipmentx', '/login', '/work-orders/71/accept', 'https://example.com']) expect(businessReturnTo(path, '/requests')).toBe('/requests')
})
it('returning through a related record reuses the original route without growing a cycle', () => {
  const original = '/requests/41?returnTo=%2Frequests%3Fpage%3D3'
  expect(relatedBusinessTo('/requests/41', '/work-orders/71', original)).toBe(original)
  expect(relatedBusinessTo('/equipment/51', '/work-orders/71?returnTo=%2Ftasks', original)).toEqual({ path: '/equipment/51', query: { returnTo: '/work-orders/71?returnTo=%2Ftasks' } })
})
it('campus_return_preserves_explicit_ids_observation_and_open_items_without_nested_cycles',()=> {
  const campus='/campus?buildingId=91&floorId=92&locationId=93&observation=INTERIOR&itemsLocationId=93&page=2'
  expect(businessReturnTo(campus,'/requests')).toBe(campus)
  const current=`/work-orders/71?returnTo=${encodeURIComponent(campus)}`
  expect(relatedBusinessTo('/requests/41',current,campus)).toEqual({path:'/requests/41',query:{returnTo:campus}})
  for(const bad of ['/campus?returnTo=%2Fcampus','/campus?locationId=0','/campus?observation=evil','/campus?floorId=2&floorId=3','/campus?itemsLocationId=3&page=-1','/campus/../login']) expect(businessReturnTo(bad,'/requests')).toBe('/requests')
})
