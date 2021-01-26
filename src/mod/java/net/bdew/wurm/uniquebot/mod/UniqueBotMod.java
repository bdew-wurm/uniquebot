package net.bdew.wurm.uniquebot.mod;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

public class UniqueBotMod implements WurmServerMod, Initable {
    @Override
    public void init() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            // Install remote
            classPool.getCtClass("com.wurmonline.server.webinterface.RegistryStarter")
                    .getMethod("startRegistry", "(Lcom/wurmonline/server/webinterface/WebInterfaceImpl;Ljava/net/InetAddress;I)V")
                    .instrument(new ExprEditor() {
                        @Override
                        public void edit(MethodCall m) throws CannotCompileException {
                            if (m.getMethodName().equals("bind"))
                                m.replace("$proceed($$); net.bdew.wurm.uniquebot.mod.UniqueRemoteImpl.register($0);");
                        }
                    });

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

    }
}
