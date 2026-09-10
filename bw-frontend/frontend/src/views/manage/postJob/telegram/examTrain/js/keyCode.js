const numberKey = [
  {key:'1', code: '1',coding: '21',tip: '食指'},
  {key:'2', code: '2',coding: '22',tip: '食指'},
  {key:'3', code: '3',coding: '23',tip: '中指'},
  {key:'4', code: '4',coding: '24',tip: '无名指'},
  {key:'5', code: '5',coding: '25',tip: '小指'},
  {key:'6', code: '6',coding: '31',tip: '食指'},
  {key:'7', code: '7',coding: '32',tip: '食指'},
  {key:'8', code: '8',coding: '33',tip: '中指'},
  {key:'9', code: '9',coding: '34',tip: '无名指'},
  {key:'0', code: '0',coding: '35',tip: '小指'},
];

const specialKey = [
  {key:'控制', code: 'control',coding: '11',tip: '食指'},
  {key:'F1', code: 'F1',coding: '12',tip: '食指'},
  {key:'F2', code: 'F2',coding: '13',tip: '中指'},
  {key:'F3', code: 'F3',coding: '14',tip: '无名指'},
  {key:'F4', code: 'F4',coding: '15',tip: '小指'},
  {key:'空格', code: 'enter',coding: '41',tip: '拇指'},
  {key:'?', code: '?',coding: '42',tip: '食指'},
  {key:'FM', code: '/',coding: '43',tip: '中指'},
  {key:'开始', code: 'start',coding: '44',tip: '无名指'},
  {key:'句号', code: 'period',coding: '45',tip: '小指'},
];

const fingerKey = [
    [
      {key:'1', code: '1',coding: '21',tip: '食指'},
      {key:'6', code: '6',coding: '31',tip: '食指'}
    ],
    [
      {key:'2', code: '2',coding: '22',tip: '食指'},
      {key:'7', code: '7',coding: '32',tip: '食指'}
    ],
    [
      {key:'3', code: '3',coding: '23',tip: '中指'},
      {key:'8', code: '8',coding: '33',tip: '中指'}
    ],
    [
      {key:'4', code: '4',coding: '24',tip: '无名指'},
      {key:'9', code: '9',coding: '34',tip: '无名指'}
    ],
    [
      {key:'5', code: '5',coding: '25',tip: '小指'},
      {key:'0', code: '0',coding: '35',tip: '小指'}
    ],
    [
      {key:'控制', code: 'control',coding: '11',tip: '食指'},
      {key:'F1', code: 'F1',coding: '12',tip: '食指'},
      {key:'F2', code: 'F2',coding: '13',tip: '中指'},
      {key:'F3', code: 'F3',coding: '14',tip: '无名指'},
      {key:'F4', code: 'F4',coding: '15',tip: '小指'},
      {key:'空格', code: 'enter',coding: '41',tip: '拇指'},
      {key:'?', code: '?',coding: '42',tip: '食指'},
      {key:'FM', code: '/',coding: '43',tip: '中指'},
      {key:'开始', code: 'start',coding: '44',tip: '无名指'},
      {key:'句号', code: 'period',coding: '45',tip: '小指'},
    ],
];

const electronKey = [
    [
      {key:'control', text: '控制', code: '11'},
      {key:'F1', text:'F1', code: '12'},
      {key:'F2', text:'F2', code: '13'},
      {key:'F3', text:'F3', code: '14'},
      {key:'F4', text:'F4', code: '15'}
    ],
    [
      {key:'1', text:'1', _code: [0], code: '21'},
      {key:'2', text:'2', _code: [0,0], code: '22'},
      {key:'3', text:'3', _code: [1], code: '23'},
      {key:'4', text:'4', _code: [1,0], code: '24'},
      {key:'5', text:'5', _code: [0,1], code: '25'}
    ],
    [
      {key:'6', text:'6', _code: [0], code: '31'},
      {key:'7', text:'7', _code: [0,0], code: '32'},
      {key:'8', text:'8', _code: [1], code: '33'},
      {key:'9', text:'9', _code: [1,0], code: '34'},
      {key:'0', text:'0', _code: [0,1], code: '35'}
    ],
    [
      {key:'enter', text:'', _code: [], code: '41'},
      {key:'?', text:'?', _code: [0,0,0], code: '42'},
      {key:'FM', text:'/', _code: [1,1], code: '43'},
      {key:'start', text:'开始', _code: [1,0,1], code: '44'},
      {key:'period', text:'句号', _code: [0,1,1], code: '45'}
    ]
];

const codeInKey = {
  '11': {key:'control', text: '控制', code: '11'},
  '12': {key:'F1', text:'F1', code: '12'},
  '13': {key:'F2', text:'F2', code: '13'},
  '14': {key:'F3', text:'F3', code: '14'},
  '15': {key:'F4', text:'F4', code: '15'},
  '21': {key:'1', text:'1', _code: [0], code: '21'},
  '22': {key:'2', text:'2', _code: [0,0], code: '22'},
  '23': {key:'3', text:'3', _code: [1], code: '23'},
  '24': {key:'4', text:'4', _code: [1,0], code: '24'},
  '25': {key:'5', text:'5', _code: [0,1], code: '25'},
  '31': {key:'6', text:'6', _code: [0], code: '31'},
  '32': {key:'7', text:'7', _code: [0,0], code: '32'},
  '33': {key:'8', text:'8', _code: [1], code: '33'},
  '34': {key:'9', text:'9', _code: [1,0], code: '34'},
  '35': {key:'0', text:'0', _code: [0,1], code: '35'},
  '41': {key:'enter', text:'', _code: [], code: '41'},
  '42': {key:'?', text:'?', _code: [0,0,0], code: '42'},
  '43': {key:'FM', text:'/', _code: [1,1], code: '43'},
  '44': {key:'start', text:'开始', _code: [1,0,1], code: '44'},
  '45': {key:'period', text:'句号', _code: [0,1,1], code: '45'},
};

const codeOnKey = {
  '10001': '开始',
  '01': 'A',
  '1000': 'B',
  '1010': 'C',
  '100': 'D',
  '0': 'E',
  '0010': 'F',
  '110': 'G',
  '0000': 'H',
  '00': 'I',
  '0111': 'J',
  '101': 'K',
  '0100': 'L',
  '11': 'M',
  '10': 'N',
  '111': 'O',
  '0110': 'P',
  '1101': 'Q',
  '010': 'R',
  '000': 'S',
  '1': 'T',
  '001': 'U',
  '0001': 'V',
  '011': 'W',
  '1001': 'X',
  '1011': 'Y',
  '1100': 'Z',
}

export {
  numberKey,specialKey,electronKey,fingerKey, codeInKey, codeOnKey
}