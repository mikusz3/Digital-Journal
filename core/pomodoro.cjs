function remaining(timer, now=Date.now()) { return timer?.running ? Math.max(0,timer.deadline-now) : Math.max(0,timer?.remaining || 0); }
function change(timer, input, now=Date.now()) {
  if (input.action==='reset') return null;
  if (input.action==='start') {
    if (!Number.isInteger(input.minutes) || input.minutes<5 || input.minutes>30) throw new Error('Choose a whole number from 5 to 30 minutes.');
    return { profileId:input.profileId,taskId:input.taskId,title:input.title,minutes:input.minutes,remaining:input.minutes*60000,deadline:now+input.minutes*60000,running:true };
  }
  if(!timer) throw new Error('Start a timer first.');
  const left=remaining(timer,now);
  if(input.action==='pause')return {...timer,remaining:left,running:false};
  if(input.action==='resume' && left>0)return {...timer,deadline:now+left,running:true};
  throw new Error('Start a new focus session.');
}
module.exports={remaining,change};
