import { expect, it } from 'vitest'
import raw from '../../public/campus/manifest.json'
import { ResourceCache } from './ResourceCache'
it('classroom_and_office_counts', () => {
  const classrooms = raw.locations.filter(l => String(l.templateKey) === 'classroom')
  const offices = raw.locations.filter(l => String(l.templateKey) === 'office')
  expect(classrooms.length).toBe(57); expect(offices.length).toBe(15)
  expect(raw.locations.filter(l => String(l.templateKey) === 'teaching-restroom')).toHaveLength(30)
  expect(raw.locations.filter(l => String(l.templateKey) === 'teaching-lobby')).toHaveLength(3)
})
it('actual_classroom_office_and_common_geometry_matches_facilities', async () => {
  const {createClassroom} = await import('./templates/Classroom')
  const {createOffice} = await import('./templates/Office')
  const {createTeachingCommon} = await import('./templates/TeachingCommon')
  const pool = new ResourceCache()
  const count = (room: ReturnType<typeof createClassroom>) => {
    const counts: Record<string,number> = {}
    room.group.traverse(node => { const f=node.userData.facility; if(typeof f==='string')counts[f]=(counts[f]??0)+1 })
    room.dispose(); return counts
  }
  expect(count(createClassroom(pool))).toMatchObject({DESK:30,CHAIR:30,COMPUTER:1,AC:1,LIGHT:6,SOCKET:4})
  expect(count(createOffice(pool))).toMatchObject({DESK:4,CHAIR:4,AC:1,LIGHT:2,SOCKET:4})
  const publicCounts=count(createTeachingCommon(pool,'teaching-restroom'))
  expect(publicCounts.AC??0).toBe(0);expect(publicCounts.BASIN).toBe(2);expect(publicCounts.CUBICLE).toBe(2)
  expect(count(createTeachingCommon(pool,'teaching-lobby')).AC??0).toBe(0)
  expect(pool.stats().entries).toBe(0)
})
