const text=(value:unknown)=>typeof value==='string'?value:undefined
export function switchPersonnelCategory(q:Record<string,unknown>,category:string):Record<string,string>{return {category,...(text(q.enabled)?{enabled:String(q.enabled)}:{}),...(text(q.keyword)?{keyword:String(q.keyword)}:{}),page:'1'}}
export function selectPersonnelRole(q:Record<string,unknown>,role:string):Record<string,string>{return {category:'ALL',...(role?{role}:{}),...(role==='TECHNICIAN'&&text(q.reviewStatus)?{reviewStatus:String(q.reviewStatus)}:{}),...(text(q.enabled)?{enabled:String(q.enabled)}:{}),...(text(q.keyword)?{keyword:String(q.keyword)}:{}),page:'1'}}
export const pendingPersonnelQuery=():Record<string,string>=>({category:'ALL',role:'TECHNICIAN',reviewStatus:'PENDING',page:'1'})
export const personnelDetailLink=(id:string,back:string)=>({path:`/admin/personnel/${id}`,query:{listReturn:personnelListReturn(back)}})
export const personnelListReturn=(value:unknown)=>typeof value==='string'&&value.length<4096&&/^\/admin\/personnel(?:\?[^\r\n#\\]*)?$/.test(value)?value:'/admin/personnel'
