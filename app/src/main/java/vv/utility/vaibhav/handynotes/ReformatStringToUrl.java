package vv.utility.vaibhav.handynotes;

/**
 * Created by Vaibhav on 3/9/2016.
 */
public class ReformatStringToUrl {

    public String reformatString(String inputString){
        String finalUrl = "";
        String tempUrl = "";
        char [] inputArray = inputString.toCharArray();

        for(int i = 0; i < inputArray.length; i++){
            if(inputArray[i] != ' '){
                tempUrl = tempUrl+inputArray[i];
            }
            else{
                tempUrl = tempUrl+"%20";
            }
        }

        finalUrl = tempUrl;

        return finalUrl;
    }
}
