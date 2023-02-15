package cs_agent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cs_agent.PreMain.global_file_translate;
import static cs_agent.PreMain.print_error;

public class Translator {
   private static Translator translator = null;
   private static Map map = new HashMap();
   private static Map regex = new HashMap();

   public Translator() {
      String trans_file = global_file_translate;

      try {
         Path p = Paths.get(trans_file);
         List lines = Files.readAllLines(p, StandardCharsets.UTF_8);

         for (Object o : lines) {
            String line = (String) o;
            if (!line.startsWith("#")) {
               String[] inputs = line.split("\t+", 2);
               if (inputs.length == 2) {
                  if (inputs[0].startsWith("[regex] ")) {
                     Pattern pattern = Pattern.compile("(?m)" + inputs[0].substring(8).replace("_LF_", "\n").replace("_TAB_", "\t"));
                     regex.put(pattern, inputs[1].replace("_LF_", "\n").replace("_TAB_", "\t"));
                  } else {
                     map.put(inputs[0], inputs[1].replace("_LF_", "\n").replace("_TAB_", "\t"));
                  }
               }
            }
         }
      } catch (Exception exception) {
         print_error(String.format("Translator fucked up: %s \n", exception) );
      }

   }

   public static String translate(String str) {
      if (translator == null) {
         translator = new Translator();
      }

      if (str != null && str.length() != 0) {
         String result = (String)map.get(str.replace("\n", "_LF_").replace("\t", "_TAB_"));
         return result == null ? str : result;
      } else {
         return str;
      }
   }

   public static String regexTranslate(String str) {
      String trans = translate(str);
      if (!trans.equals(str)) {
         return trans;
      } else {
         Iterator var2 = regex.keySet().iterator();

         Pattern pattern;
         Matcher matcher;
         do {
            if (!var2.hasNext()) {
               return str;
            }

            pattern = (Pattern)var2.next();
            matcher = pattern.matcher(str);
         } while(!matcher.find());

         String result = (String)regex.get(pattern);

         for(int i = 0; i <= matcher.groupCount(); ++i) {
            result = result.replace(String.format("${%d}", i), matcher.group(i));
         }

         return result;
      }
   }
}
