# AndroidP2pTest
两个android客户端进行点对点(p2p)及时通信，通信内容可以文字，图片，文件。

快速开始

运行项目，在log中查看到
onId data=Nzs3jmD5g876WCpcAAEH
其中data后为当前设备id,id 每次运行都会变，把id赋值给
RtcActivity.callerId ，运行到另一台设备上，启动后会自己建立连接

待log出现以下输出，表明连接建立，
registerObserver onStateChangeOPEN
Peer onDataChannel=org.webrtc.DataChannel@bf04e5c

可在输入框输入文字，->发送，另一设备将会收到并显示，发送图片，发送文件按钮一样,内部已处理64k 自动分包问题


![Image text](https://raw.githubusercontent.com/hongmaju/light7Local/master/img/productShow/20170518152848.png)


