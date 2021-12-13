package czm.record;

import android.content.Context;
import android.os.Build;
import android.os.CpuUsageInfo;
import android.util.Log;
import android.os.HardwarePropertiesManager;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;

public class CPUUtils {
    private static long currentJiffies = 0;
    private static long lastJiffies = 0;
    private static long currentIdle = 0;
    private static long lastIdle = 0;
    private static final String TAG = "CPUTools";
    static float getCPUUsageByCmd(){
        Process proc;
        String line = "";
        String cmd = "top -n 1";
        boolean isInfoLine = true;
        int cpuIndex = 0;
        int index = 0;
        float usage = 0;
        try {
            //process = Runtime.getRuntime().exec(new String[]{"sh","-c",cmd});
            proc = Runtime.getRuntime().exec(cmd);
            //Process proc = Runtime.getRuntime().exec("su");
            DataOutputStream dos = new DataOutputStream(proc.getOutputStream());
            dos.writeBytes(cmd + "\n");
            dos.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            while((line = br.readLine()) != null){
                line = line.trim();
                System.out.println("line" + index + ": " + line);
                index++;
                String[] resp = line.split(" +"); // match 1 more "\\s"
                if(isInfoLine){
                    for(int i = 0;i < resp.length;i++){
                        //System.out.println("i = " + i + " str = " + resp[i]);
                        if(resp[i].contains("CPU")){
                            cpuIndex = i;
                            //System.out.println("index = " + cpuIndex);
                            isInfoLine = false;
                            break;
                        }
                    }
                } else{
                    if(resp.length < cpuIndex) break; // last line
                    usage += Float.parseFloat(resp[cpuIndex]);
                }

            }
            try {
                proc.waitFor();
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }catch (IOException e){
            e.printStackTrace();
        }
        return usage;
    }

    static float readCPUAvalByStat() {
        try {
            RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
            String load = reader.readLine();

            String[] toks = load.split(" +");  // Split on one or more spaces

            long idle1 = Long.parseLong(toks[4]);
            long cpu1 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            try {
                Thread.sleep(360);
            } catch (Exception e) {}

            reader.seek(0);
            load = reader.readLine();
            reader.close();

            toks = load.split(" +");

            long idle2 = Long.parseLong(toks[4]);
            long cpu2 = Long.parseLong(toks[2]) + Long.parseLong(toks[3]) + Long.parseLong(toks[5])
                    + Long.parseLong(toks[6]) + Long.parseLong(toks[7]) + Long.parseLong(toks[8]);

            return (float)(cpu2 - cpu1) / ((cpu2 + idle2) - (cpu1 + idle1));

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return 0;
    }

    static String ReadCPUinfo()
    {
        ProcessBuilder cmd;
        String result="";

        try{
            String[] args = {"/system/bin/cat", "/proc/cpuinfo"};
            cmd = new ProcessBuilder(args);

            Process process = cmd.start();
            InputStream in = process.getInputStream();
            byte[] re = new byte[1024];
            while(in.read(re) != -1){
                System.out.println(new String(re));
                result = result + new String(re);
            }
            in.close();
        } catch(IOException ex){
            ex.printStackTrace();
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    static float readCPUAvalByHPM(Context context){
        HardwarePropertiesManager hpm = (HardwarePropertiesManager)context.getSystemService(Context.HARDWARE_PROPERTIES_SERVICE);
        CpuUsageInfo[] cpuUsageInfos = hpm.getCpuUsages();
        long total = 0,active = 0;
        for(int i = 0;i < cpuUsageInfos.length;i++){
            total += cpuUsageInfos[i].getTotal();
            active += cpuUsageInfos[i].getActive();
        }
        System.out.println("CPU active:" + active + " total: " + total);
        return (float) active/total;
    }
}
