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
    finger:'l1'
  },
  {
    text:'W',
    keyCode: 87,
    finger:'l2'
  },
  {
    text:'E',
    keyCode: 69,
    finger:'l3'
  },
  {
    text:'R',
    keyCode: 82,
    finger:'l4'
  },
  {
    text:'T',
    keyCode: 84,
    finger:'l4'
  },
  {
    text:'Y',
    keyCode: 89,
    finger:'r4 rsplit'
  },
  {
    text:'U',
    keyCode: 85,
    finger:'r4'
  },
  {
    text:'I',
    keyCode: 73,
    finger:'r3'
  },
  {
    text:'O',
    keyCode: 79,
    finger:'r2'
  },
  {
    text:'P',
    keyCode: 80,
    finger:'r1'
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
    finger:'l1'
  },
  {
    text:'S',
    keyCode: 83,
    finger:'l2'
  },
  {
    text:'D',
    keyCode: 68,
    finger:'l3'
  },
  {
    text:'F',
    keyCode: 70,
    finger:'l4'
  },
  {
    text:'G',
    keyCode: 71,
    finger:'l4'
  },
  {
    text:'H',
    keyCode: 72,
    finger:'r4 rsplit'
  },
  {
    text:'J',
    keyCode: 74,
    finger:'r4'
  },
  {
    text:'K',
    keyCode: 75,
    finger:'r3'
  },
  {
    text:'L',
    keyCode: 76,
    finger:'r2'
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
    finger:'l2'
  },
  {
    text:'C',
    keyCode: 67,
    finger:'l3'
  },
  {
    text:'V',
    keyCode: 86,
    finger:'l4'
  },
  {
    text:'B',
    keyCode: 66,
    finger:'l4'
  },
  {
    text:'N',
    keyCode: 78,
    finger:'r4 rsplitLast'
  },
  {
    text:'M',
    keyCode: 77,
    finger:'r4'
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
export {
  first,second,third,fourth,fifth,last,numberKey,letterKey,specialKey
}