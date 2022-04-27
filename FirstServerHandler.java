package ru.gb.storage.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import ru.gb.storage.commons.message.*;

import java.io.*;
import java.nio.file.Path;


public class FirstServerHandler extends SimpleChannelInboundHandler<Message> {
    RandomAccessFile accessFile;
    Boolean isAuth = false;
    TextMessage answerMessage;
    String pathFromClient;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("New active channel");
        TextMessage answer = new TextMessage();
        answer.setText("Successfully connection");
        ctx.writeAndFlush(answer);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws IOException {
        if (msg instanceof TextMessage) {
            TextMessage message = (TextMessage) msg;
            System.out.println("incoming text message: " + message.getText());
            ctx.writeAndFlush(msg);
        }


        // Авторизация нового клиента
        if (msg instanceof AuthMessage){
            AuthMessage authMessage = (AuthMessage) msg;
                    String nick = DBservices.getNickByLoginAndPass(authMessage.getLogin(), authMessage.getPassword());
                    if (nick != null) {
                        answerMessage.setText("/authOk " + nick);
                        isAuth = true;
                    }else {
                        answerMessage.setText("/authNotOk");
                    }
                   ctx.writeAndFlush(answerMessage);

        }

        // Если клиент хочит считать файл из сервера, то в запросе имя файла и направление - чтение.
        // Далее по стандартному пути, как в примере на лекции.
        // Если наоборот, записать на сервер, то в запросе имя файла и направление - запись. В этом случае сервер пересылает клиенту его же
        // FileRequestMessage. В хендлере клиента те же функции, что и на сервере (почти) и запрос отрабатывает как сервер, т.е. пересылает файл..
        if (msg instanceof FileRequestMessage){
            FileRequestMessage frm = (FileRequestMessage) msg;
            if (isAuth) {
                if (frm.getDirection()) {
                    final File file = new File(frm.getPath());
                    accessFile = new RandomAccessFile(file, "r");
                    sendFile(ctx);
                }else {
                    pathFromClient = frm.getPath();
                    ctx.writeAndFlush(frm);
                }
            }else{
                System.out.println("Не авторизованный пользователь пытается получить доступ");
            }
        }



        if (msg instanceof FileContentMessage) {
            FileContentMessage fileContentMessage = (FileContentMessage) msg;
            try (final RandomAccessFile accessFile = new RandomAccessFile("D:\\Server\\" + pathFromClient, "rw")) {
                System.out.println(fileContentMessage.getStartPosition());
                accessFile.seek(fileContentMessage.getStartPosition());
                accessFile.write(fileContentMessage.getContent());
                if (fileContentMessage.isLast()) {
                    ctx.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    private void sendFile(ChannelHandlerContext ctx) throws IOException {
        if (accessFile!=null){
            final byte[] fileContent;
            final long available = accessFile.length()-accessFile.getFilePointer();
            if(available > 64 * 1024){
                fileContent = new byte[64 * 1024];
            }else {
                fileContent = new byte[(int) available];
            }
            final FileContentMessage message = new FileContentMessage();
            message.setStartPosition(accessFile.getFilePointer());
            accessFile.read(fileContent);
            message.setContent(fileContent);
            final boolean last = accessFile.getFilePointer() == accessFile.length();
            message.setLast(last);
            ctx.channel().writeAndFlush(message).addListener((ChannelFutureListener) channelFuture -> {
                if(!last) {
                    sendFile(ctx);
                }
            });
            if (last){
                accessFile.close();
                accessFile=null;
            }
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws IOException {
        System.out.println("client disconnect");
        if (accessFile!=null){
            accessFile.close();
        }
    }
}
