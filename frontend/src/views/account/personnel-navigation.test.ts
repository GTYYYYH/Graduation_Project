import { expect,it } from 'vitest'
import { switchPersonnelCategory,selectPersonnelRole,pendingPersonnelQuery,personnelDetailLink,personnelListReturn } from './personnel-navigation'
import { businessReturnTo } from '../../router/businessNavigation'
it('category_clears_role_review_and_keeps_enabled_keyword',()=>{
  expect(switchPersonnelCategory({role:'TECHNICIAN',reviewStatus:'PENDING',enabled:'false',keyword:'搜索',page:'4'},'REPORTER_COMPLETED')).toEqual({category:'REPORTER_COMPLETED',enabled:'false',keyword:'搜索',page:'1'})
  expect(selectPersonnelRole({category:'TECHNICIAN_COMPLETED',reviewStatus:'PENDING',page:'3'},'REPORTER')).toEqual({category:'ALL',role:'REPORTER',page:'1'})
  expect(pendingPersonnelQuery()).toEqual({category:'ALL',role:'TECHNICIAN',reviewStatus:'PENDING',page:'1'})
})
it('list_detail_and_business_return_preserve_independent_queries',()=>{
  const list='/admin/personnel?category=REPORTER_COMPLETED&enabled=false&page=3'
  const link=personnelDetailLink('12',list)
  expect(personnelListReturn(link.query.listReturn)).toBe(list)
  const detail='/admin/personnel/12?category=REQUESTS&keyword=灯&page=2&listReturn='+encodeURIComponent(list)
  expect(businessReturnTo(detail,'/requests')).toBe(detail)
  expect(businessReturnTo('https://example.com','/requests')).toBe('/requests')
})
