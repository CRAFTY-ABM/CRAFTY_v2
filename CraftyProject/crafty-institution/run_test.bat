@echo off

::mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\main\resources\Institutions.json src\main\resources\fuzzy_rules.fcl"

:: Zero policy
::mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\test\java\testmodel\testInputData\zeroPolicyTest.json src\test\java\testmodel\testInputData\fuzzy_rules.fcl src\test\java\testmodel\testOutputData\zero_policy.csv"


:: Cabon tax
::mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\test\java\testmodel\testInputData\Carbon_tax01.json src\test\java\testmodel\testInputData\fuzzy_rules.fcl src\test\java\testmodel\testOutputData\carbon_tax01.csv"


:: crop subsidy
:: mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\test\java\testmodel\testInputData\crop_subsidy01.json src\test\java\testmodel\testInputData\fuzzy_rules.fcl src\test\java\testmodel\testOutputData\crop_subsidy01.csv"


:: meat subsidy
::mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\test\java\testmodel\testInputData\meat_subsidy01.json src\test\java\testmodel\testInputData\fuzzy_rules.fcl src\test\java\testmodel\testOutputData\meat_subsidy01.csv"

:: crop and meat subsidy
::mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\test\java\testmodel\testInputData\crop_meat_subsidy01.json src\test\java\testmodel\testInputData\fuzzy_rules.fcl src\test\java\testmodel\testOutputData\crop_meat_subsidy01.csv"

:: crop and meat subsidy, and carbon tax
mvn test-compile exec:java -Dexec.mainClass="testmodel.TestRunner" -Dexec.classpathScope=test -Dexec.args="src\test\java\testmodel\testInputData\crop_meat_carbon01.json src\test\java\testmodel\testInputData\fuzzy_rules.fcl src\test\java\testmodel\testOutputData\crop_meat_carbon01.csv"
