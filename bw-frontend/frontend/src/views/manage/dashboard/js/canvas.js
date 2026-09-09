export default function CanvasMove (ctx) {
  this.p1 = [Math.random()*250,0]
  this.c = [Math.random()*250,Math.random()*400]
  this.c2 = [Math.random()*250,Math.random()*400]
  this.p2 = [Math.random()*250,400]
  this.wh = Math.random()*(50-20)+20
}
