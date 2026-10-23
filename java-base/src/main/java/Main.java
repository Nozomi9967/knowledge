import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StreamTokenizer;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

// 注意类名必须为 Main, 不要有任何 package xxx 信息
public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer st = new StreamTokenizer(new BufferedReader(new InputStreamReader(System.in)));
        st.nextToken();int t=(int)st.nval;
        while(t-->0){
            st.nextToken();int n=(int)st.nval;
            int[] a=new int[n];
            Map<Integer,Integer>[] cnt=new HashMap[n];
            long[] b=new long[n];
            int[] s=new int[n];
            int count=0,index=0,tx=0;
            for(int i=0;i<n;i++){
                st.nextToken();a[i]=(int)st.nval;
                cnt[i]=new HashMap<Integer,Integer>();
                if(i>0) s[i]+=s[i-1]+a[i]-a[i-1];
//                if(t==1&&i>0) System.out.println(i+" "+(a[i]-a[i-1]));
                for(int j=i-1;j>=0;j--){
                    int sub=s[i]-s[j];
                    int num=i-j;
                    if(sub%num==0){
                        int x=sub/num;
//                        if(t==1) System.out.println(sub+" "+num);
                        int y=cnt[j].getOrDefault(x, 1)+1;
                        int preY=cnt[i].getOrDefault(x, 1);
//                        if(t==1){
//                            System.out.println(i+" "+j);
//                            System.out.println(sub+" "+num);
//                        }
                        if(y>preY) cnt[i].put(x,y);
                        if(y>count){
                            count=y;
                            index=i;
                            tx=x;
                        }
                    }
                }
            }
            for(int i=0;i<n;i++){
                st.nextToken();b[i]=(long)st.nval;
            }
            int tail=a[index]+tx*(n-index-1);
//            if(t==1) System.out.println(tail);
            long ans=0;
            for(int i=n-1;i>=0;i--){
                if(a[i]!=tail) ans+=b[i];
                tail-=tx;
            }
            System.out.println(ans);
        }


    }
}