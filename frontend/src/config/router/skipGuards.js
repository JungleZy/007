import {PubSub} from "../../common/utils/PubSub.js";

let flag = true;
export default function skipGuards(to, from, skip) {
  flag = true;
  closeTheoryEditPage(to, from, skip);
  closeHandKeyPatTrainPage(to, from, skip);
  closeTelexTrainPage(to, from, skip);
  closeExamTrainPage(to, from, skip);
  closeReceiveTrainPage(to, from, skip);
  closehanziPage(to, from, skip);
  closeHandKeyPatPostTrainPage(to, from, skip);
  closeWordingTrainPage(to, from, skip);
  // closeBroadcastTrainPage(to, from, skip);
  if (flag) {
    skip()
  }
}

function closeTheoryEditPage(to, from, skip) {
  if (from.path.indexOf("theoryEdit") > -1) {
    PubSub.subscribe("callback_theoryEdit_close", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_theoryEdit_close");
        skip()
      }
    });
    PubSub.publish("send_theoryEdit_close");
    flag = false;
  }
}

/** 手键拍发训练订阅消息 */
function closeHandKeyPatTrainPage(to, from, skip) {
  if (from.path.indexOf("handKeyTrain") > -1) {
    PubSub.subscribe("callback_handKeyTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_handKeyTrainPage");
        skip()
      }
    });
    PubSub.publish("send_handKeyTrainPage");
    flag = false;
  }
}

//电传拍发连贯训练
function closeTelexTrainPage(to, from, skip) {
  // from.path.indexOf("telexTrain") > -1||
  if (from.path.indexOf("telexTrain") > -1||from.path.indexOf("postJobTelexTrain") >-1 ) {
    PubSub.subscribe("callback_closeTelexTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_closeTelexTrainPage");
        skip()
      }
    });
    PubSub.publish("send_telexTrainPage");
    flag = false;
  }
}

//电键拍发综合练习
function closeExamTrainPage(to, from, skip) {
  if (from.path.indexOf("examComplexTrain") > -1 || from.path.indexOf("examPostJobTrain") > -1 || from.path.indexOf("militaryTermTrain") > -1) {
    PubSub.subscribe("callback_closeExamTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_closeExamTrainPage");
        skip()
      }
    });
    PubSub.publish("send_examTrainPage");
    flag = false;
  }
}
//汉字训练
function closehanziPage(to, from, skip) {
  //from.path.indexOf("practice") > -1 ||
  // from.path.indexOf("wbPracticeTwo") > -1||
  if (from.path.indexOf("postWuBiPractice") > -1 ||from.path.indexOf("postEnglishPractice") > -1||from.path.indexOf("postPractice") > -1) {
    PubSub.subscribe("callback_closehanziPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_closehanziPage");
        skip()
      }
    });
    PubSub.publish("send_hanziPage");
    flag = false;
  }
}
//通用报语训练
function closeWordingTrainPage(to, from, skip) {
  if (from.path.indexOf("wordingTrain") > -1 ) {
    PubSub.subscribe("callback_closeWordingTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_closeWordingTrainPage");
        skip()
      }
    });
    PubSub.publish("send_wordingTrainPage");
    flag = false;
  }
}

/** 收报训练订阅消息 */
function closeReceiveTrainPage(to, from, skip) {
  if (from.path.indexOf("receiveTrain") > -1 || from.path.indexOf("ReceivePostTrain") > -1) {
    PubSub.subscribe("callback_receiveTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_receiveTrainPage");
        skip()
      }
    });
    PubSub.publish("send_receiveTrainPage");
    flag = false;
  }
}

/** 岗位手键拍发训练订阅消息 */
function closeHandKeyPatPostTrainPage(to, from, skip) {
  if (from.path.indexOf("handKeyPostJobTrain") > -1) {
    PubSub.subscribe("callback_handKeyPostTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_handKeyPostTrainPage");
        skip()
      }
    });
    PubSub.publish("send_handKeyPostTrainPage");
    flag = false;
  }
}

/** 通播训练 */
function closeBroadcastTrainPage(to, from, skip) {
  const userInfo = JSON.parse(localStorage.getItem('userInfo'))
  if (from.path.indexOf("broadcastTeachTrain") > -1&&from.query.createUserId===userInfo.id) {
    PubSub.subscribe("callback_BroadcastTrainPage", (e) => {
      if (e) {
        PubSub.unsubscribe("callback_BroadcastTrainPage");
        skip()
      }
    });
    PubSub.publish("send_BroadcastTrainPage");
    flag = false;
  }
}
