import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
public class A {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String b = a("0d7aa1e62363565216ef40629ff4088a" + "924723355" + "10" + "com.kandian.vodapp" + "update");
		System.out.println("kk " + b);
	}
	
	public static String a(String paramString)
	  {
	    StringBuffer localStringBuffer = new StringBuffer("");
	    try
	    {
	      MessageDigest localMessageDigest = MessageDigest.getInstance("MD5");
	      localMessageDigest.update(paramString.getBytes());
	      byte[] arrayOfByte = localMessageDigest.digest();
	      for (int i = 0; i < arrayOfByte.length; i++)
	      {
	        int j = arrayOfByte[i];
	        if (j < 0)
	          j += 256;
	        if (j < 16)
	          localStringBuffer.append("0");
	        localStringBuffer.append(Integer.toHexString(j));
	      }
	      String str = localStringBuffer.toString();
	      return str;
	    }
	    catch (NoSuchAlgorithmException localNoSuchAlgorithmException)
	    {
	      localNoSuchAlgorithmException.printStackTrace();
	    }
	    return localStringBuffer.toString();
	  }
}
