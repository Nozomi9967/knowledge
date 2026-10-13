import java.util.*;

// 注意类名必须为 Main, 不要有任何 package xxx 信息
public class Main {
//    public static int[] cnt=new int[100001];

    public static int compute(int x){
        return x*(x-1)/2;
    }

    public static int bs(int x){
        int l=2,r=Math.min(x/2,100000);
        while(l<=r){
            int mid=l+(r-l)/2;
            if(compute(mid)>=x) r=mid-1;
            else l=mid+1;
        }
        return l;
    }

    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int t=in.nextInt();
//        for(int i=2;i<100001;i++){
//            cnt[i]= i*(i-1)/2;
//        }
        while(t-->0){
            int n=in.nextInt();
            int y=0,c=0;
            int nt= n/2;
            int i=bs(nt);
            for(;i>=2;i--){
                int ci=compute(i);
                if(n%ci==0){
                    y=i;
                    c=n/ci;
                    break;
                }
            }
            if(y+c>100000) System.out.println(-1);
            else{
                System.out.println(y+c);
                while(y-->0) System.out.print('y');
                while(c-->0) System.out.print('c');
                System.out.println();
            }
        }
    }
}