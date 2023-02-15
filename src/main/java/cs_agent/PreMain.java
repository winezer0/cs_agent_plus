package cs_agent;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.ProtectionDomain;
import java.security.PublicKey;
import java.util.*;

public class PreMain
{
    public static String VERSION = "cs_agent 0.0.8 20221008"; //定义版本号
    public static String config_file = "cs_agent.ini"; //定义配置文件名称
    public static String package_name = "cs_agent"; //包名,下面有修改后的代码使用

    public static String global_file_translate = "resources/translate_zh.txt"; //默认的翻译文件,全局变量提供给Translator使用

    public static void print_warn(String var0) {
        System.out.println("\u001b[01;33m[!]\u001b[0m Agent " + scrub(var0));
    }

    public static void print_info(String s) {
        System.out.println("\u001b[01;34m[+]\u001b[0m Agent " + scrub(s));
    }

    public static void print_error(String s) {
        System.out.println("\u001b[01;31m[-]\u001b[0m Agent " + scrub(s));
    }

    public static String scrub(String s) {
        return s == null ? null : s.replace('\u001b', '.');
    }

    //从配置文件读取原始bytes[]密钥
    public static byte[] string2Bytes(String string){
        //String string = "94, -104, 25, 74, 1, -58, -76, -113, -91, -126, -90, -87, -4, -69, -110, -42";
        String[] list = string.split(",");
        byte[] bytes = new byte[list.length];
        for(int i = 0; i < list.length; ++i) {
            int a = Integer.parseInt(list[i].trim());
            bytes[i] = (byte) a;
        }
        return bytes;
    }

    //根据CS原始密钥比特 计算key字符串
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    //根据CS原始密钥比特 计算水印
    private static int bytesToInt(byte[] bytes) {
        //byte[] bytes = new byte[]{0, 1, -122, -96};
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int anInt = buffer.getInt(0);
        return anInt;
    }

    //根据CS原始密钥比特 计算水印hash字符串
    private static String bytesToString(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        for(int i = 0; i < bytes.length; ++i) {
            char aByte = (char) bytes[i];
            if (aByte > 0) {
                stringBuffer.append(aByte);
            }
        }
        return stringBuffer.toString();
    }

    public static void premain(final String agentArgs, final Instrumentation inst) throws IOException {
        //输出版本信息
        print_info(String.format("Current Version Is [%s]", VERSION));

        //从参数输入配置文件名称
        if (agentArgs != null && !"".equals(agentArgs)){
            config_file = agentArgs;
        }

        inst.addTransformer(new CobaltStrikeTransformer(), true);
    }

    static class CobaltStrikeTransformer implements ClassFileTransformer {
        private final ClassPool classPool;
        private final String Auto = "Auto";

        //输入解密AUTH用的参数
        private Boolean need_translation = false;
        private Boolean need_fix_xss_rce = false;
        private Boolean need_crack_auth = false;
        private Boolean need_crack_version = false;
        private Boolean need_crack_agent = false;

        private Double attrib_cs_version = 4.5 ;

        private String sleevedKey = Auto;
        private String watermarkHash  = Auto;
        private String watermark = "000000";

        //固定替换路径文件
        private final String dest_bhelp = "resources/bhelp.txt";         //直接部署即可替换
        private final String dest_default_cna = "scripts/default.cna";      //直接部署即可替换
        private final String dest_default_rpt = "scripts/default.rpt";      //直接部署即可替换
        private final String dest_bdetails = "resources/bdetails.txt";  //直接部署即可替换

        public CobaltStrikeTransformer() throws IOException {
            this.classPool = ClassPool.getDefault();
            //判断配置文件是否存在
            boolean Skip_Handle = false;
            if (Files.notExists(Paths.get(config_file))) {
                print_error(String.format("Config File [%s] Is Not Exist! Will Skip Handle !!!", config_file));
                // System.exit(0);  //退出进程  // return;   //跳过处理
                Skip_Handle = true;
            }else {
                print_info(String.format("Config File Use [%s]", config_file));
            }

            //默认会调用当前目录下的default.cna等文件,因此需要删除
            //直接部署即可替换
            String[] dest_file_list = {this.dest_bdetails, this.dest_bhelp, this.dest_default_cna , this.dest_default_rpt};
            for (String filename : dest_file_list) {
                if (filename != null && !filename.equals("") && Files.exists(Paths.get(filename))) {
                    Files.delete(Paths.get(filename));
                    print_warn(String.format("Initial Config Delete File [%s]", filename));
                }
            }

            //配置文件参数字符串
            String str_attrib_cs_version = "attrib.cs_version";

            String str_file_translate_zh = "file.translate_zh";
            String str_file_inner_cna_zh = "file.inner_cna_zh";
            String str_file_inner_rpt_zh = "file.inner_rpt_zh";
            String str_file_inner_details_zh = "file.inner_details_zh";
            String str_file_inner_help_zh = "file.inner_help_zh";
            String str_need_fix_xss_rce = "need.fix_xss_rce";
            String str_need_translation = "need.translation";
            String str_need_crack_agent = "need.crack_agent";
            String str_need_crack_auth = "need.crack_auth";
            String str_need_crack_version = "need.crack_version";

            String str_beacon_watermark_hash = "beacon.watermark.hash";
            String str_beacon_watermark_value = "beacon.watermark.value";
            String str_beacon_sleeved_key = "beacon.sleeved.key";

            //配置文件参数的初始值
            String file_translate_zh = global_file_translate;
            String file_bhelp_zh = this.dest_bhelp.split("\\.",2)[0] + "_zh." + this.dest_bhelp.split("\\.",2)[1];
            String file_bdetails_zh = this.dest_bdetails.split("\\.",2)[0] + "_zh." + this.dest_bdetails.split("\\.",2)[1];
            String file_default_cna_zh = this.dest_default_cna.split("\\.",2)[0] + "_zh." + this.dest_default_cna.split("\\.",2)[1];
            String file_default_rpt_zh = this.dest_default_rpt.split("\\.",2)[0] + "_zh." + this.dest_default_rpt.split("\\.",2)[1];

            //退出进程
            if (Skip_Handle) {
                print_error(String.format("Generate Config Demo File [%s] and Skip Handle!!!", config_file));
                //创建配置文件模板
                //Properties properties = new Properties(); //不支持按字母排序
                Properties properties = new Properties(){  //支持按字母排序
                    private static final long serialVersionUID = 1L;
                    @Override
                    public synchronized Enumeration<Object> keys() {
                        return Collections.enumeration(new TreeSet<>(super.keySet()));
                    }
                };

                properties.setProperty(str_need_fix_xss_rce, "N");
                properties.setProperty(str_need_translation, "N");
                properties.setProperty(str_need_crack_auth, "N");
                properties.setProperty(str_need_crack_version, "N");
                properties.setProperty(str_need_crack_agent, "N");

                properties.setProperty(str_attrib_cs_version, String.valueOf(this.attrib_cs_version));
                properties.setProperty(str_beacon_sleeved_key, this.sleevedKey);
                properties.setProperty(str_beacon_watermark_hash, this.watermarkHash);
                properties.setProperty(str_beacon_watermark_value, this.watermark);

                properties.setProperty(str_file_inner_help_zh, file_bhelp_zh);
                properties.setProperty(str_file_inner_details_zh, file_bdetails_zh);
                properties.setProperty(str_file_inner_cna_zh, file_default_cna_zh);
                properties.setProperty(str_file_inner_rpt_zh, file_default_rpt_zh);
                properties.setProperty(str_file_translate_zh, file_translate_zh);

                //使用Properties生产配置文件
                properties.store(new FileWriter(config_file), "cs_agent config");
                return;
            }

            //加载配置文件
            Properties prop = new Properties();
            try {
                FileInputStream fileInputStream = new FileInputStream(config_file);
                prop.load(fileInputStream);
            }
            catch (IOException ioException) {
                print_error(String.format("Failed to load file [%s]!", config_file));
                throw ioException;
            }

            //初始化参数
            String param;
            //获取CS版本号,获取失败就保持原版本
            param = prop.getProperty(str_attrib_cs_version);

            if (param != null && !param.equals("") && !param.equalsIgnoreCase(Auto)){ this.attrib_cs_version = Double.valueOf(param); }
            //根据版本号内置一些默认密钥
            if (this.attrib_cs_version == 4.0) { this.sleevedKey = "1be5be52c6255c33558e8a1cb667cb06"; }
            else if (this.attrib_cs_version == 4.1) { this.sleevedKey = "80e32a742060b884419ba0c171c9aa76"; }
            else if (this.attrib_cs_version == 4.2) { this.sleevedKey = "b20d487addd4713418f2d5a3ae02a7a0"; }
            else if (this.attrib_cs_version == 4.3) { this.sleevedKey = "3a4425490f389aeec312bdd758ad2b99"; }
            else if (this.attrib_cs_version == 4.4) { this.sleevedKey = "5e98194a01c6b48fa582a6a9fcbb92d6"; }
            else if (this.attrib_cs_version == 4.5) {
                this.sleevedKey = "f38eb3d1a335b252b58bc2acde81b542";
                this.watermarkHash = "BeudtKgqnlm0Ruvf+VYxuw==" ;
                // 从CS4.5泄漏的auth文件读取并输出,发现该watermarkHash值为 BeudtKgqnlm0Ruvf+VYxuw==,
                // 与CS4.5agent中提供的watermarkHash值 MYhXSMGVvcr7PtOTMdABvA== 不匹配
                // 因此需要进一步确认该watermarkHash字符串的作用。
            }
            else if (this.attrib_cs_version == 4.6) {
                this.sleevedKey = "b9aa33080a5a90313ef19dc0627d2ea1";
                this.watermarkHash = "s59l5iq1ejZ3OQd/sgvNag==qﾐￖh" ;
            }
            else if (this.attrib_cs_version == 4.7) {
                this.sleevedKey = "8638b511e05b557bf970c418356d44f4";
                this.watermarkHash = "" ;
            }

            //读取配置文件  //初始化功能开关
            param = prop.getProperty(str_need_crack_agent);
            this.need_crack_agent = (param != null  && !param.equalsIgnoreCase(Auto) && param.equalsIgnoreCase("y"));

            param = prop.getProperty(str_need_crack_auth);
            this.need_crack_auth = (param != null  && !param.equalsIgnoreCase(Auto) && param.equalsIgnoreCase("y"));

            param = prop.getProperty(str_need_crack_version);
            this.need_crack_version = (param != null  && !param.equalsIgnoreCase(Auto) && param.equalsIgnoreCase("y"));


            param = prop.getProperty(str_need_translation);
            this.need_translation = (param != null  && !param.equalsIgnoreCase(Auto) && param.equalsIgnoreCase("y"));

            param = prop.getProperty(str_need_fix_xss_rce);
            this.need_fix_xss_rce = (param != null  && !param.equalsIgnoreCase(Auto) && param.equalsIgnoreCase("y"));

            //读取配置文件 //初始化相关功能参数
            param = prop.getProperty(str_beacon_sleeved_key);
            if (param != null && !param.equals("") && !param.equalsIgnoreCase(Auto)){
                //判断该参数是否包含多个逗号,是的话说明使用的是原始字符串，就需要进行字符串转换处理
                int length = 16;
                if (param.trim().split(",").length > length - 1){
                    this.sleevedKey = bytesToHex(string2Bytes(param));
                    print_info(String.format("Input sleevedKey From %s to %s", param , this.sleevedKey));
                }else{
                    this.sleevedKey = param;
                }
            }

            param = prop.getProperty(str_beacon_watermark_hash);
            if (param != null  && !param.equals("") && !param.equalsIgnoreCase(Auto)){
                //判断该参数是否包含多个逗号,是的话说明使用的是原始字符串，就需要进行字符串转换处理
                int length = 24;
                if (param.trim().split(",").length > length - 1){
                    this.watermarkHash = bytesToString(string2Bytes(param));
                    print_info(String.format("Input watermarkHash From %s to %s", param , this.watermarkHash));
                }else{
                    this.watermarkHash = param;
                }
            }

            param = prop.getProperty(str_beacon_watermark_value);
            if (param != null && !param.equals("") && !param.equalsIgnoreCase(Auto)){
                //判断该参数是否包含多个逗号,是的话说明使用的是原始字符串，就需要进行字符串转换处理
                int length = 4;
                if (param.trim().split(",").length > length - 1){
                    this.watermark = String.valueOf(bytesToInt(string2Bytes(param)));
                    print_info(String.format("Input watermark From %s to %s", param , this.watermark));
                    //like 0, 1, -122, -96 == 100000
                }else{
                    this.watermark = param;
                }
            }

            //赋值汉化文件
            param = prop.getProperty(str_file_translate_zh);
            if (param != null && !param.equals("") && !param.equalsIgnoreCase(Auto)){
                file_translate_zh = param;
                global_file_translate = file_translate_zh;
            }

            param  = prop.getProperty(str_file_inner_help_zh);
            if (param != null && !param.equals("") && !param.equalsIgnoreCase(Auto)){ file_bhelp_zh = param; }

            param = prop.getProperty(str_file_inner_details_zh);
            if (param != null  && !param.equals("") && !param.equalsIgnoreCase(Auto)){ file_bdetails_zh = param; }

            param = prop.getProperty(str_file_inner_cna_zh);
            if (param != null && !param.equals("")  && !param.equalsIgnoreCase(Auto) ){ file_default_cna_zh = param; }

            param = prop.getProperty(str_file_inner_rpt_zh);
            if (param != null && !param.equals("")  && !param.equalsIgnoreCase(Auto) ){ file_default_rpt_zh = param; }

            //检查翻译所需的配置文件是否存在
            if (this.need_translation) {
                //输出用户输入的的文件参数
                String[] must_file_list = {file_default_cna_zh,file_default_rpt_zh, file_translate_zh, file_bdetails_zh, file_bhelp_zh};
                print_info(String.format("File List About Translation : %s ", Arrays.toString(must_file_list)));

                boolean HasFileNotExist = false;
                for (String filename : must_file_list) {
                    if (filename == null || filename.equals("") ||  Files.notExists(Paths.get(filename))) {
                        print_error(String.format("Translation Need File [%s] Is Not Exist! Will Quit Process!!!", filename));
                        HasFileNotExist = true;
                    }
                }
                if (HasFileNotExist) {System.exit(0); }

                //拷贝配置使用的cna等文件
                Files.copy(Paths.get(file_default_cna_zh),  Paths.get(this.dest_default_cna), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(Paths.get(file_default_rpt_zh),  Paths.get(this.dest_default_rpt), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(Paths.get(file_bdetails_zh),  Paths.get(this.dest_bdetails), StandardCopyOption.REPLACE_EXISTING);
                Files.copy(Paths.get(file_bhelp_zh),  Paths.get(this.dest_bhelp), StandardCopyOption.REPLACE_EXISTING);
            } else  {
                //如果不开启翻译,使用jar包内部的文件
                print_info(String.format("Use English File From JAR Package : %s",Arrays.toString(dest_file_list)));
            }
        }

        //翻译函数
        CtBehavior insertTranslateCommand(final CtBehavior ctMethod, final int n, final Boolean regex) throws CannotCompileException {
            StringBuilder stringBuffer = new StringBuilder();
            stringBuffer.append("{");
            stringBuffer.append("ClassLoader classLoader = ClassLoader.getSystemClassLoader();");
            stringBuffer.append(String.format("Class translator = classLoader.loadClass(\"%s.Translator\");", package_name));
            if (regex) {
                stringBuffer.append("java.lang.reflect.Method method = translator.getDeclaredMethod(\"regexTranslate\",new Class[]{String.class});");
            }
            else {
                stringBuffer.append("java.lang.reflect.Method method = translator.getDeclaredMethod(\"translate\",new Class[]{String.class});");
            }
            stringBuffer.append(String.format("if($%d instanceof String){$%d = (String)method.invoke(null, new Object[]{$%d});}", n, n, n));
            stringBuffer.append("}");
            StringBuilder outer = new StringBuilder();
            outer.append("if ((javax.swing.table.DefaultTableCellRenderer.class.isAssignableFrom($0.getClass())  && !sun.swing.table.DefaultTableCellHeaderRenderer.class.isAssignableFrom($0.getClass())) || javax.swing.text.DefaultStyledDocument.class.isAssignableFrom($0.getClass())  || javax.swing.tree.DefaultTreeCellRenderer.class.isAssignableFrom($0.getClass())  || $0.getClass().getName().equals(\"javax.swing.plaf.synth.SynthComboBoxUI$SynthComboBoxRenderer\")) {} else");
            outer.append(stringBuffer);
            try {
                ctMethod.insertBefore(outer.toString());
            }
            catch (Exception exception) {
                ctMethod.insertBefore(stringBuffer.toString());
            }
            return ctMethod;
        }

        @Override
        public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain, final byte[] classfileBuffer) throws IllegalClassFormatException {
                try {
                if (className == null) {
                    return classfileBuffer;
                }

                //绕过Agent限制
                if (this.need_crack_agent) {
                    if(className.equals("sun/management/RuntimeImpl")){
                        print_info("Bypass Agent Inspect ...");
                        final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        final CtMethod ctMethod = ctClass.getDeclaredMethod("getInputArguments");
                        this.classPool.importPackage("java.util.ArrayList");
                        ctMethod.insertAfter("ArrayList ret = new ArrayList();for (int i=0; i < $_.size(); i++) {   if($_.get(i).toString().toLowerCase().contains(\"-javaagent:\")) continue;   ret.add($_.get(i));}$_ = ret;");
                        return ctClass.toBytecode();
                    }
                }

                //修改版本号
                        if( className.equals("aggressor/Aggressor")) {
                            print_info("Crack Soft Version ...");
                            CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            CtField ctfield = ctClass.getDeclaredField("VERSION");
                            ctClass.removeField(ctfield);

                            CtField newCtfield = new CtField(this.classPool.getCtClass("java.lang.String"), "VERSION", ctClass);
                            ctfield.setModifiers(Modifier.PUBLIC);
                            ctClass.addField(newCtfield);

                            String func1 = "public static void setVERSION(String VERSION) { this.VERSION = VERSION; }";
                            CtMethod setVersion = CtNewMethod.make(func1, ctClass);
                            ctClass.addMethod(setVersion);

                            String func2 = "public static void getVERSION() { return AggressorVERSION; }";
                            CtMethod getVersion = CtNewMethod.make(func2, ctClass);
                            ctClass.addMethod(getVersion);

                            CtMethod ctMethod = ctClass.getDeclaredMethod("main");
                            ctMethod.insertAfter("setVersion(\"4.5 (20211214) Licensed\");");

//                            public static void main(String[] var0) {
//                                Aggressor var1 = new Aggressor();
//                                var1.A(var0);
//                            }


//                            ctClass.removeField(ctfield);
//                            CtField newCtfield = new CtField(this.classPool.getCtClass("java.lang.String"), "VERSION", ctClass);
//                            newCtfield.setModifiers(Modifier.PUBLIC);
//                            ctClass.addField(newCtfield);


                            ctClass.writeFile();
                            return ctClass.toBytecode();
                        }

                //认证破解
                if (this.need_crack_auth) {
                    if( className.equals("common/Authorization")) {
                        print_info("Crack Authorization ...");
                        CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        String func = "public static byte[] hex2bytes(String s) {" +
                                "int len = s.length();   " +
                                "byte[] data = new byte[len / 2];   " +
                                "for (int i = 0; i < len; i += 2) {" +
                                "       data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));   " +
                                "}   " +
                                "return data;}";
                        CtMethod hex2bytes = CtNewMethod.make(func, ctClass);
                        ctClass.addMethod(hex2bytes);
                        CtConstructor ctConstructor = ctClass.getDeclaredConstructor(new CtClass[0]);

                        String watermarkHashString;
                        if (attrib_cs_version < 4.5){
                            watermarkHashString = "";
                        }else {
                            watermarkHashString = "$0.watermarkHash = \"" + this.watermarkHash + "\";";
                        }

                        ctConstructor.setBody("{$0.watermark = " + this.watermark + ";" +
                                "$0.validto = \"forever\";" +
                                "$0.valid = true;" +
                                 watermarkHashString +
                                "common.MudgeSanity.systemDetail(\"valid to\", \"perpetual\");" +
                                "common.MudgeSanity.systemDetail(\"id\", String.valueOf($0.watermark));" +
                                "common.SleevedResource.Setup(hex2bytes(\"" + this.sleevedKey + "\"));}");
                        return ctClass.toBytecode();
                    }
                }
                //开始进行翻译
                if (this.need_translation){
                    if (Files.exists(Paths.get(this.dest_bhelp)) && Files.exists(Paths.get(this.dest_bdetails))){
                        if (className.equals("beacon/BeaconCommands")){
                            print_info(String.format("Load Translation [%s,%s] ...", this.dest_bhelp ,this.dest_bdetails));
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final String[] array = new String[] { "loadCommands", "loadDetails" };
                            for (final String s : array) {
                                CtMethod ctMethod = ctClass.getDeclaredMethod(s);
                                ctMethod.instrument(new ExprEditor() {
                                    @Override
                                    public void edit(final MethodCall methodCall) throws CannotCompileException {
                                        if (methodCall.getClassName().equals("common.CommonUtils") && methodCall.getMethodName().equals("bString")) {
                                            methodCall.replace("{ $_ = new String($1, \"UTF-8\"); }");
                                        }
                                    }
                                });
                            }
                            return ctClass.toBytecode();
                        }
                    }
                    if (Files.exists(Paths.get(this.dest_default_cna)) && Files.exists(Paths.get(this.dest_default_rpt))  ) {
                        if(className.equals("sleep/runtime/ScriptLoader")){
                            print_info(String.format("Load Translation [%s,%s] ...",  this.dest_default_cna, this.dest_default_rpt));
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtMethod ctMethod = ctClass.getDeclaredMethod("getInputStreamReader");
                            ctMethod.insertBefore("setCharset(\"UTF-8\");");
                            return ctClass.toBytecode();
                        }
                    }

                    if (Files.exists(Paths.get(global_file_translate))) {
                        if (className.equals("javax/swing/AbstractButton")) {
                            print_info(String.format("Load Translation %s ...", global_file_translate));
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtMethod ctMethod = ctClass.getDeclaredMethod("setText");
                            this.insertTranslateCommand(ctMethod, 1, false);
                            ctMethod.insertBefore("{setActionCommand($1);}");
                            return ctClass.toBytecode();
                        }

                        if (className.equals("javax/swing/JLabel")) {
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtMethod ctMethod = ctClass.getDeclaredMethod("setText");
                            this.insertTranslateCommand(ctMethod, 1, false);
                            return ctClass.toBytecode();
                        }

                        if (className.equals("javax/swing/JComponent")) {
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtMethod ctMethod = ctClass.getDeclaredMethod("setToolTipText");
                            this.insertTranslateCommand(ctMethod, 1, false);
                            return ctClass.toBytecode();
                        }
                        if (className.equals("javax/swing/JOptionPane")) {
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtMethod ctMethod = ctClass.getDeclaredMethod("showOptionDialog", new CtClass[] { this.classPool.get("java.awt.Component"), this.classPool.get("java.lang.Object"), this.classPool.get("java.lang.String"), CtClass.intType, CtClass.intType, this.classPool.get("javax.swing.Icon"), this.classPool.get("java.lang.Object[]"), this.classPool.get("java.lang.Object") });
                            this.insertTranslateCommand(ctMethod, 2, true);
                            return ctClass.toBytecode();
                        }
                        if (className.equals("javax/swing/JDialog")) {
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtConstructor ctConstructor = ctClass.getDeclaredConstructor(new CtClass[] { this.classPool.get("java.awt.Frame"), this.classPool.get("java.lang.String"), CtClass.booleanType });
                            this.insertTranslateCommand(ctConstructor, 2, false);
                            return ctClass.toBytecode();
                        }
                        if (className.equals("javax/swing/JFrame")) {
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtConstructor ctConstructor = ctClass.getDeclaredConstructor(new CtClass[] { this.classPool.get("java.lang.String") });
                            this.insertTranslateCommand(ctConstructor, 1, false);
                            return ctClass.toBytecode();
                        }
                        if (className.equals("javax/swing/JEditorPane")) {
                            final CtClass ctClass = this.classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                            final CtMethod ctMethod = ctClass.getDeclaredMethod("setText");
                            this.insertTranslateCommand(ctMethod, 1, false);
                            return ctClass.toBytecode();
                        }
                    }
                }

                //开始进行XSS RCE修复
                if (this.need_fix_xss_rce){
                    if(className.equals("javax/swing/plaf/basic/BasicHTML")){
                        print_info("Patched CVE-2022-39197 XSS RCE ...");
                        ClassPool classPool = ClassPool.getDefault();
                        classPool.appendClassPath(new LoaderClassPath(loader));
                        CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer), false);
                        CtMethod ctMethod = ctClass.getDeclaredMethod("isHTMLString");
                        ctMethod.setBody("return false;");
                        return ctClass.toBytecode();
                    }
                }
            }
            catch (Exception ex) {
                System.out.printf("PreMain transform Exception: %s\n", ex);
                ex.printStackTrace();
            }
            return classfileBuffer;
        }
    }
}
