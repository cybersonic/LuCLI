component {
    function init(){
       echo("Hello from the init() function #chr(10)#");
    }

    function main(){

        echo("Hello from the main() function #chr(10)#");
    
      // some comment
        sayHello("Person");

      // Another comment
        init();
    
    }


    function sayHello(required string name){


        echo("Hello, #name#! #chr(10)#");
    }
}