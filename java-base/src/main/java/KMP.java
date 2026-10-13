import java.util.ArrayList;
import java.util.List;

public class KMP {

    public static void main(String[] args) {
        String text="ababa";
        String pattern="aba";

        int[] next=buildNext(pattern);
        printIntArr(next);
        printStringList(kmp(text,pattern,next));
    }


    // version:1
    private static void printStringList(List<String> res) {
        int n=res.size();
        for(int i=0;i<n;i++){
            if(i!=0) System.out.print(",");
            System.out.print(res.get(i));
        }
        System.out.println();
    }

    public static void printIntArr(int[] arr){
        int n=arr.length;
        for(int i=0;i<n;i++){
            if(i!=0) System.out.print(",");
            System.out.print(arr[i]);
        }
        System.out.println();
    }

    public static int[] buildNext(String pattern){
        int n = pattern.length();
        int[] next = new int[n];
        for(int i=1,j=0;i<n;i++){
            while(j>0&&pattern.charAt(j)!=pattern.charAt(i)){
                j=next[j-1];
            }

            if(pattern.charAt(j)==pattern.charAt(i)){
                j++;
            }
            next[i]=j;
        }
        return next;
    }

    public static List<String> kmp(String text,String pattern,int[] next){
        List<String> res=new ArrayList<>();
        int n=text.length(),m=pattern.length();
        for(int j=0,i=0;i<n;i++){
            while(j>0&&pattern.charAt(j)!=text.charAt(i)){
                j=next[j-1];
            }

            if(pattern.charAt(j)==text.charAt(i)){
                j++;
            }

            if(j==m){
                res.add("["+(i-m+1)+","+i+"]");
                j=next[j-1];
            }
        }
        return res;
    }

    // end
}
