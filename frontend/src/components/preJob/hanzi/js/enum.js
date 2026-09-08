import {deepClone} from "../../../../common/utils/Utils.js";
// 多一个键 74 px
// 左手  l1 小指 l2 无名指 l3 中指 l4 食指
// 右手  r1 小指 r2 无名指 r3 中指 r4 食指
const first = [
  {
    text:'Esc',
  },
  {
    text:'F1',
    style:'margin-left:60px'
  },
  {
    text:'F2',
  },
  {
    text:'F3',
  },
  {
    text:'F4',
  },
  {
    text:'F5',
    style:'margin-left:30px'
  },
  {
    text:'F6',
  },
  {
    text:'F7',
  },
  {
    text:'F8',
  },
  {
    text:'F9',
    style:'margin-left:30px'
  },
  {
    text:'F10',
  },
  {
    text:'F11',
  },
  {
    text:'F12',
  },
  {
    text:'PrtSrc SysRq',
    style:'margin-left:20px'
  },
  {
    text:'Scroll Lock',
  },
  {
    text:'Pause Break',
  },
]
const second = [
  {
    text:'~',
    text2:'·',
    keyCode: 192,
  },
  {
    text:'!',
    text2:'1',
    keyCode: 49,
    finger:'l1'
  },
  {
    text:'@',
    text2:'2',
    keyCode: 50,
    finger:'l2'
  },
  {
    text:'#',
    text2:'3',
    keyCode: 51,
    finger:'l3'
  },
  {
    text:'$',
    text2:'4',
    keyCode: 52,
    finger:'l4'
  },
  {
    text:'%',
    text2:'5',
    keyCode: 53,
    finger:'l4'
  },
  {
    text:'^',
    text2:'6',
    keyCode: 54,
    finger:'r4 rsplit'
  },
  {
    text:'&',
    text2:'7',
    keyCode: 55,
    finger:'r4'
  },
  {
    text:'*',
    text2:'8',
    keyCode: 56,
    finger:'r3'
  },
  {
    text:'(',
    text2:'9',
    keyCode: 57,
    finger:'r2'
  },
  {
    text:')',
    text2:'0',
    keyCode: 48,
    finger:'r1'
  },
  {
    text:'——',
    text2:'_',
    keyCode: 189
  },
  {
    text:'+',
    text2:'=',
    keyCode: 187
  },
  {
    text:'Back Space',
    style:'width:'+(50+74)+'px',
    keyCode: 8
  },
  // {
  //   text:'Insert',
  //   style:'margin-left:20px'
  // },
  // {
  //   text:'Home',
  // },
  // {
  //   text:'Page Up',
  // },
  {
    text:'Num Lock',
    style2: 'margin-left:40px',
    keyCode: 144
  },
  {
    text:'/',
    keyCode: 111
  },
  {
    text:'*',
    keyCode: 106
  },
  {
    text:'-',
    keyCode: 109
  },


]
const numberKey = (deepClone(second)).splice(1,10)
const third = [
  {
    text:'Tab',
    style:'width:'+(50+37)+'px',
    keyCode: 9,
  },
  {
    text:'Q',
    keyCode: 81,
    finger:'l1',
    isimg:true,
    color:'#4258f9, #1a30d2'
  },
  {
    text:'W',
    keyCode: 87,
    finger:'l2',isimg:true,
    color:'#4258f9, #1a30d2'
  },
  {
    text:'E',
    keyCode: 69,
    finger:'l3',
    isimg:true,
    color:'#4258f9, #1a30d2'
  },
  {
    text:'R',
    keyCode: 82,
    finger:'l4',
    isimg:true,
    color:'#4258f9, #1a30d2'
  },
  {
    text:'T',
    keyCode: 84,
    finger:'l4',
    isimg:true,
    color:'#4258f9, #1a30d2'
  },
  {
    text:'Y',
    keyCode: 89,
    finger:'r4 rsplit',
    isimg:true,
    color:'#b266ff, #7730bf'
  },
  {
    text:'U',
    keyCode: 85,
    finger:'r4',
    isimg:true,
    color:'#b266ff, #7730bf'
  },
  {
    text:'I',
    keyCode: 73,
    finger:'r3',
    isimg:true,
    color:'#b266ff, #7730bf'
  },
  {
    text:'O',
    keyCode: 79,
    finger:'r2',
    isimg:true,
    color:'#b266ff, #7730bf'
  },
  {
    text:'P',
    keyCode: 80,
    finger:'r1',
    isimg:true,
    color:'#b266ff, #7730bf'
  },
  {
    text:'{',
    text2:'[',
    keyCode: 219
  },
  {
    text:'}',
    text2:']',
    keyCode: 221
  },
  {
    text:'|',
    text2:" \\",
    style:'width:'+(50+37)+'px',
    keyCode: 220
  },
  // {
  //   text:'Delete',
  //   style:'margin-left:20px'
  // },
  // {
  //   text:'End',
  // },
  // {
  //   text:'Page Down',
  // },
  {
    text:'7',
    style2: 'margin-left:40px',
    keyCode: 103
  },
  {
    text:'8',
    text2:``,
    keyCode: 104
  },
  {
    text:'9',
    keyCode: 105
  },



]
const fourth = [
  {
    text:'CapsLock',
    style:'width:'+(50+74)+'px',
    keyCode: 20
  },
  {
    text:'A',
    keyCode: 65,
    finger:'l1',
    isimg:true,
    color:'#e8a829, #a06e0b'
  },
  {
    text:'S',
    keyCode: 83,
    finger:'l2',
    isimg:true,
    color:'#e8a829, #a06e0b'
  },
  {
    text:'D',
    keyCode: 68,
    finger:'l3',
    isimg:true,
    color:'#e8a829, #a06e0b'
  },
  {
    text:'F',
    keyCode: 70,
    finger:'l4',
    isimg:true,
    color:'#e8a829, #a06e0b'
  },
  {
    text:'G',
    keyCode: 71,
    finger:'l4',
    isimg:true,
    color:'#e8a829, #a06e0b'
  },
  {
    text:'H',
    keyCode: 72,
    finger:'r4 rsplit',
    isimg: true,
    color:'#00b882, #00523a'
  },
  {
    text:'J',
    keyCode: 74,
    finger:'r4',
    isimg:true,
    color:'#00b882, #00523a'
  },
  {
    text:'K',
    keyCode: 75,
    finger:'r3',
    isimg:true,
    color:'#00b882, #00523a'
  },
  {
    text:'L',
    keyCode: 76,
    finger:'r2',
    isimg:true,
    color:'#00b882, #00523a'
  },
  {
    text:':',
    text2:';',
    keyCode: 186,
    finger:'r1'
  },
  {
    text:'’‘',
    text2:'‘',
    tstyle:'line-height:40px',
    tstyle2:'line-height:13px',
    keyCode: 222
  },
  {
    text:'Enter',
    style:'width:'+(50+74)+'px',
    keyCode: 13,
    location: true
  },
  {
    text:'4',
    style2: 'margin-left:40px',
    keyCode: 100
  },
  {
    text:'5',
    keyCode: 101
  },
  {
    text:'6',
    keyCode: 102
  },



]
const fifth = [
  {
    text:'shift',
    style:'width:'+(50+74+37)+'px',
    keyCode: 16,
    location: 1,
  },
  {
    text:'Z',
    keyCode: 90,
    finger:'l1'
  },
  {
    text:'X',
    keyCode: 88,
    finger:'l2',
    isimg:true,
    color:'#0078b8, #004063'
  },
  {
    text:'C',
    keyCode: 67,
    finger:'l3',
    isimg:true,
    color:'#0078b8, #004063'
  },
  {
    text:'V',
    keyCode: 86,
    finger:'l4',
    isimg:true,
    color:'#0078b8, #004063'
  },
  {
    text:'B',
    keyCode: 66,
    finger:'l4',
    isimg:true,
    color:'#0078b8, #004063'
  },
  {
    text:'N',
    keyCode: 78,
    finger:'r4 rsplitLast',
    isimg:true,
    color:'#0078b8, #004063'
  },
  {
    text:'M',
    keyCode: 77,
    finger:'r4',
    isimg:true,
    color:'#00b882, #00523a'
  },
  {
    text:'<',
    text2:',',
    keyCode: 188,
    finger:'r3'
  },
  {
    text:'>',
    text2:'.',
    keyCode: 190,
    finger:'r2'
  },
  {
    text:'?',
    text2:'/',
    keyCode: 191,
    finger:'r1'
  },
  {
    text:'Shift',
    style:'width:'+(50+74+37)+'px',
    keyCode: 16,
    location: 2
  },
  // {
  //   text:'up',
  //   style:'margin-left:'+(56+20)+'px;'+'margin-right:76px'
  // },
  {
    text:'1',
    style2: 'margin-left:40px',
    keyCode: 97
  },
  {
    text:'2',
    keyCode: 98
  },
  {
    text:'3',
    keyCode: 99
  },
]
const last = [
  {
    text:'Ctrl',
    style:'width:'+(70)+'px',
    keyCode: 17,
    location: 1
  },
  {
    text:'Win',
    style:'width:'+(70)+'px',
    keyCode: 91,
    location: 1
  },
  {
    text:'alt',
    style:'width:'+(70)+'px',
    keyCode: 18,
    location: 1
  },
  {
    text:' ',
    style:'width:'+(50+378)+'px',
    keyCode: 32,
    finger:'kg'
  },
  {
    text:'alt',
    style:'width:'+(70)+'px',
    keyCode: 18,
    location: 2
  },
  {
    text:'Win',
    style:'width:'+(70)+'px',
    keyCode: 92,
    location: 2
  },
  {
    text:'菜单',
    style:'width:'+(70)+'px',
    keyCode: 93,
    location: 0
  },
  {
    text:'Ctrl',
    style:'width:'+(70)+'px',
    keyCode: 17,
    location: 2
  },
  // {
  //   text:'l',
  //   style:'margin-left:20px'
  // },
  // {
  //   text:'down',
  // },
  // {
  //   text:'r',
  // },
  {
    text:'0',
    style:'width:'+(50+74)+'px',
    style2: 'margin-left:40px',
    keyCode: 96,
    location: 3
  },
  {
    text:'.',
    keyCode: 110,
    location: 3
  },

]
const letter1 = (deepClone(third)).splice(1,10)
const letter2 = (deepClone(fourth)).splice(1,9)
const letter3 = (deepClone(fifth)).splice(1,7)
const letterKey = [...letter1,...letter2,...letter3]
const specialKey = [
  {
    text:' ',
    text2:'Space ',
    style:'width:'+(50+378)+'px',
    keyCode: 32,
    explain:"间隔键:拍发间隔或空白时用！",
    finger:''
  },
  {
    text:'?',
    text2:'/',
    keyCode: 191,
    explain:"改错建:拍发改错符号或使用斜线时用！"
  },
  {
    text:'Enter',
    style:'width:'+(50+74)+'px',
    keyCode: 13,
    location: true,
    explain:"换行建:拍发完一行或需要换行时用！"
  },
  {
    text:'Tab',
    style:'width:'+(50+37)+'px',
    keyCode: 9,
    explain:"回动键:拍发完一行或需要回行时用！",
  },
  {
    text:'——',
    text2:'_',
    keyCode: 189,
    explain:"页标键:拍发页标符号或使用横线使时用！"
  },
]

//口诀
const KJ = [
  {
    key:'G',
    text:'王旁青头戋（兼）五一',
    type:'0'
  },
  {
    key:'F',
    text:'土士二干十寸雨',
    type:'0'
  },
  {
    key:'D',
    text:'大犬三羊古石场',
    type:'0'
  },
  {
    key:'S',
    text:'木丁西',
    type:'0'
  },
  {
    key:'A',
    text:'工戈草头右框七',
    type:'0'
  },
  {
    key:'H',
    text:'目具上止卜虎皮',
    type:'1'
  },
  {
    key:'J',
    text:'日早两竖与虫依',
    type:'1'
  },
  {
    key:'K',
    text:'口与川，字根稀',
    type:'1'
  },
  {
    key:'L',
    text:'田甲方框四车力',
    type:'1'
  },
  {
    key:'M',
    text:'山由贝，下框几',
    type:'1'
  },
  {
    key:'T',
    text:'禾竹一撇双人立，反问条头共三一',
    type:'2'
  },
  {
    key:'R',
    text:'白手看头三二斤',
    type:'2'
  },
  {
    key:'E',
    text:'月彡（衫）乃用家衣底',
    type:'2'
  },
  {
    key:'W',
    text:'人和八，三四里',
    type:'2'
  },
  {
    key:'Q',
    text:'金勺缺点五尾鱼，犬旁留叉一点夕，氏无七',
    type:'2'
  },
  {
    key:'Y',
    text:'严文方广在四一，高头一捺谁人去',
    type:'3'
  },
  {
    key:'U',
    text:'立辛两点六门疒',
    type:'3'
  },
  {
    key:'I',
    text:'水旁兴头小倒立',
    type:'3'
  },
  {
    key:'O',
    text:'火业头，四点米',
    type:'3'
  },
  {
    key:'P',
    text:'之字军盖建道底，摘礻(示）衤（衣）',
    type:'3'
  },
  {
    key:'N',
    text:'已半巳满不出已，左框折尸心和羽',
    type:'4'
  },
  {
    key:'B',
    text:'子耳了也框向上',
    type:'4'
  },
  {
    key:'V',
    text:'女刀久臼山朝西',
    type:'4'
  },
  {
    key:'C',
    text:'又巴马，丢矢矣',
    type:'4'
  },
  {
    key:'X',
    text:'慈母无心弓和匕，幼无力',
    type:'4'
  },
]
export {
  first,second,third,fourth,fifth,last,numberKey,letterKey,specialKey,KJ
}