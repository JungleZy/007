import codebook from './entering-codebook.json'

export default class Homophone {
  constructor() {
    this.pyvalue = codebook.pinyin.syllables
    this.pystr = codebook.pinyin.characters
  }
  convertPY(a) {
    if (null == a || 0 == a.length) return ''
    var b = a.charAt(0)
    if (255 >= a.charCodeAt(0)) return b
    for (a = 0; a < this.pystr.length; a++)
      if (0 <= this.pystr[a].indexOf(b)) return this.pyvalue[a]
    return ''
  }
  convertPYs(a) {
    a = a.split('')
    for (var b = [], e = [], c, d = 0; d < a.length; d++)
      if ((c = this.convertPY(a[d]))) b.push(c), e.push(c.charAt(0))
    return [b.join(''), e.join('')]
  }
}
