export default function canvas (ctx,img) {
  this.x = 0
  this.y = 0

}
canvas.prototype.ballmove = ()=>{
  this.ctx.clearRect(0,0,600,600)
  this.ctx.beginPath()
  this.n+=0.01
  if(this.m==1){
    this.x+=1+this.n
  }else {
    this.x-=1+this.n
  }
  this.y=this.y+2*Math.tan(Math.PI*this.n)
  this.ctx.arc(this.x,this.y,this.r,0,2*Math.PI)
  this.ctx.fill()
}