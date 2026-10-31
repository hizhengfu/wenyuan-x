// @vitest-environment happy-dom
import { afterEach, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { defineComponent, defineAsyncComponent, h, KeepAlive, onActivated, onDeactivated, onUnmounted, ref, Teleport, Transition } from 'vue';
import { createMemoryHistory, createRouter, onBeforeRouteLeave, RouterView } from 'vue-router';
import AppRoutePage from '@/components/app/AppRoutePage.vue';
const mounted:any[]=[];
afterEach(()=>{mounted.splice(0).forEach(w=>w.unmount());vi.restoreAllMocks();});
function inspect(component:any,attrs:Record<string,any>={}) {
 const warnings:string[]=[];
 const Host=defineComponent({setup:()=>()=>h(Transition,{css:false,appear:true},{default:()=>h(AppRoutePage,{component,...attrs})})});
 const w=mount(Host,{global:{stubs:{transition:false},config:{warnHandler:(message)=>warnings.push(message)}}});mounted.push(w);return {w,warnings};
}
it('the original direct fragment binding reproduces both reported warnings',()=>{
 const warnings:string[]=[];const Fragment=defineComponent({render:()=>[h('main','list'),h('aside','drawer') ]});
 const Host=defineComponent({render:()=>h(Transition,{css:false,appear:true},{default:()=>h(Fragment,{class:'flex-auto'})})});
 const w=mount(Host,{global:{stubs:{transition:false},config:{warnHandler:m=>warnings.push(m)}}});mounted.push(w);
 expect(warnings.some(m=>m.includes('Extraneous non-props attributes'))).toBe(true);
 expect(warnings.some(m=>m.includes('non-element root'))).toBe(true);
});
it('class style and accessible attributes reach the container without falling through to a fragment',()=>{
 const Fragment=defineComponent({render:()=>[h('main','list'),h('aside','drawer')]});
 const {w,warnings}=inspect(Fragment,{class:'flex-auto',style:{minHeight:'200px'},id:'route-content','aria-label':'当前页面'});
 expect(w.get('#route-content').classes()).toContain('flex-auto');expect(w.get('#route-content').attributes('style')).toContain('min-height: 200px');expect(w.get('#route-content').attributes('aria-label')).toBe('当前页面');expect(w.get('main').attributes('class')).toBeUndefined();expect(warnings).toEqual([]);
});
it.each(['text','teleport'])('%s root route remains usable without attribute or transition warnings',kind=>{
 const Page=defineComponent({render:()=>kind==='text'?'提示正文':h(Teleport,{to:'body'},[h('aside',{'data-route-dialog':'yes'},'原件窗口')])});
 const {w,warnings}=inspect(Page,{class:'flex-auto'});expect(w.find('.app-route-page').exists()).toBe(true);
 if(kind==='text')expect(w.text()).toContain('提示正文');else expect(document.querySelector('[data-route-dialog]')?.textContent).toBe('原件窗口');expect(warnings).toEqual([]);
});
it('async route components still resolve under the transition container',async()=>{
 const Async=defineAsyncComponent(async()=>defineComponent({render:()=>[h('main','异步资料'),h('aside','窗口')]}));const {w,warnings}=inspect(Async,{class:'flex-auto'});await flushPromises();expect(w.text()).toContain('异步资料');expect(warnings).toEqual([]);
});
it('real router keeps props, leave hooks, per-path cached state and activation cleanup',async()=>{
 const events:string[]=[],warnings:string[]=[];
 const Page=defineComponent({props:{id:String},setup(props){const count=ref(0);onActivated(()=>events.push('active:'+props.id));onDeactivated(()=>events.push('inactive:'+props.id));onUnmounted(()=>events.push('unmount:'+props.id));onBeforeRouteLeave(()=>{events.push('leave:'+props.id);});return()=>[h('button',{onClick:()=>count.value++},`${props.id}:${count.value}`),h('aside','原件')];}});
 const router=createRouter({history:createMemoryHistory(),routes:[{path:'/item/a',component:Page,props:{id:'a'}},{path:'/item/b',component:Page,props:{id:'b'}}]});
 const Host=defineComponent({components:{RouterView,Transition,KeepAlive,AppRoutePage},template:'<RouterView v-slot="{ Component, route }"><Transition name="fade" :duration="1" mode="out-in"><KeepAlive><AppRoutePage :component="Component" :key="route.path" class="flex-auto" /></KeepAlive></Transition></RouterView>'});
 await router.push('/item/a');await router.isReady();const w=mount(Host,{global:{plugins:[router],stubs:{transition:false},config:{warnHandler:m=>warnings.push(m)}}});mounted.push(w);await flushPromises();
 await w.get('button').trigger('click');expect(w.get('button').text()).toBe('a:1');
 await router.push('/item/b');await new Promise(resolve=>setTimeout(resolve,100));await flushPromises();expect(w.get('button').text()).toBe('b:0');expect(events).toContain('leave:a');expect(events).toContain('inactive:a');expect(events).not.toContain('unmount:a');
 await router.push('/item/a');await new Promise(resolve=>setTimeout(resolve,100));await flushPromises();expect(w.get('button').text()).toBe('a:1');expect(events.filter(x=>x==='active:a')).toHaveLength(2);expect(warnings).toEqual([]);
 w.unmount();expect(events).toContain('unmount:a');expect(events).toContain('unmount:b');
});
it('KeepAlive max eviction still releases old route instances',async()=>{
 const released:string[]=[];const key=ref('a');
 const Page=defineComponent({props:{id:String},setup(p){onUnmounted(()=>released.push(p.id!));return()=>[h('main',p.id),h('aside')];}});
 const Host=defineComponent({setup:()=>()=>h(KeepAlive,{max:1},{default:()=>h(AppRoutePage,{component:h(Page,{id:key.value}),key:key.value})})});
 const w=mount(Host);mounted.push(w);key.value='b';await flushPromises();expect(w.text()).toBe('b');expect(released).toEqual(['a']);
});
