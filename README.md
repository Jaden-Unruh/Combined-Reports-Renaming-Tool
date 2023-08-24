# Combined Rename Reports Tool
A tool to rename AHERA, EISA, FCA Appendix B, and Space Utilization Reports to the standard
## Setup
Java SE 17 is required to run this program. An installer for Temurin/OpenJDK 17 should be included with the file, running the installer will open a window guiding you through the installation. Leaving everything as default should work perfectly, just click through the pages. If the installer is not included, it can be downloaded from [here](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.8%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.8_7.msi). After installing Temurin/Java 17, the `.JAR` should run just by double-clicking.
## GUI and How to Use
After double-clicking the `JAR`, a window titled "Rename Reports Tool" will open. It will have four prompts, as described below:
1. Select an input directory: `Select...`
	* Click on the select button to open a file prompt, navigate to and select the desired input directory. The input directory should have the file structure as described in [File Structure](#File-Structure).
2. Select an output directory: `Select...`
	* Click on the select button to open a file prompt, navigate to and select the desired output directory. This directory does not need to be empty, but ensure that there are no files within the output directory that have the exact same name as the program will try to create, as these files may be overwritten or cause errors. Also note that the program will create a file called `rename-info.txt` within the output directory, so if such a file already exists the existing one will be deleted.
3. Select location hierarchy spreadsheet (*.xlsx): `Select...`
	* Click on the select button to open a file prompt, navigate to and select the location hierarchy spreadsheet. Note that this must be a `.xlsx` file, rather than `.xlsb`or any other spreadsheet format. This can be obtained by opening any other format in excel and selecting 'File -> Save As -> This PC' and choosing 'Excel Workbook (.xlsx)' from the drop-down. Note that this location hierarchy must include all of the sites that will be renamed - this is where the script pulls Maximo IDs, Location Numbers, and Site Names from.
4. Select what type of reports we're renaming: `Space Utilization Reports`
	* Choose what type of reports are contained in the input directory and will be renamed from the drop-down. Note that this must be the *only* type of report in input, any other PDFs may be renamed incorrectly using a slightly different scheme. `Space Utilization Reports` is selected by default as it is the first item in the list, make sure to change this if you're renaming a different type of report.

Below the four prompts are `Close` and `Run` buttons, these work as would be expected, closing the window and running the program.

## Troubleshooting
> Nothing's happening when I double click the `.JAR` file

Ensure you've installed Java as specified under [Setup](/#Setup). If you believe you have, try checking your java version:
1. Press Ctrl+R, type `cmd` and press enter - this will open a command prompt window
2. Type `java -version` and press enter
3. If you've installed java as specified, the first line under your typing should read `openjdk version "17.0.8" 2023-07-18`. If, instead, it says `'java' is not recognized as an internal...` then java is not installed.

---
> `Run` isn't doing anything

Ensure that you've selected two directories and a *.xlsx file. Spreadsheets of a different type (.xlsb, .xls, .csv, etc.) will not work.

---
> Certain PDFs are being skipped

Ensure that the file structure matches exactly as described in [File Structure](/#File-Structure). Files and folders not placed in the correct places will be ignored. Consult `rename-info.txt` to see what's been skipped.

---
> The year on some files is incorrect

This is possible. Because I'm using Optical Character Recognition (OCR) to pull the year off the first page of the report, the algorithm may not work perfectly and may, very occasionally, read some characters wrong. Ideally, it will catch its error, and you'll be one troubleshooting option down, but it's possible, for example, that it reads '2018' as '2016', and there's no way it can catch that. Hopefully, that never happens. In all the test files I ran this on (~160 or so), it didn't seem to happen.

---
> What's this `MISSING YEARS` folder?

Tesseract, the OCR library I'm using to pull years from the first page of the PDFs, isn't perfect. Sometimes it can't find a year. In this case, the program will copy that file, with the rest of the information it can get, into `MISSING YEARS`, and the user can manually open the PDF, check the first page, and fix that.

## File Structure
The selected input directory must have a file structure that matches as described below. If it does not, some files may be skipped, or the program may have an error. Skipped files may or may not be reported in `rename-info.txt`.
### For Space Utilization, AHERA, and FCA App B Reports
- Input directory
	- IA001 A site
		- 04 Report
			- Draft
				- report.pdf
			- Final
				- report.pdf
	- JS023 Another site
		- 06 Reports
			- Draft
				- report.pdf
			- Final

As shown above, the input directory should contain a series of directories titled starting with a site id. This may be followed by the site name, but does not need to be, the site name for the final rename is pulled from the location hierarchy spreadsheet. Within each site directory must be a folder titled in the format `0n Report(s)`. That is, a zero followed by another number and then the word 'report' or 'reports'. This is case insensitive. Within the reports directory should be two directories, Draft and Final. The script will first check for a pdf within 'Final', if it doesn't find one it will check 'Draft'.

### For EISA Reports
- Input directory
	- IE023 A site
		- 4 Clean and send to client - PDF
			- IE023 A site
				- report1.pdf
				- report2.pdf
				- report3.pdf
	- JS034 Another site
		- A building
			- 6 Clean and send to client - PDF
				- report34.pdf
		- Another building
			- 7 Clean and send to client - PDF
				- report23report.pdf
		- A third building
			- 5 Clean and send to client - PDF
				- report234.pdf
	- IA034 A third site
		- 3 Clean and send to client - PDF
			- report33asdf.pdf
			- 304 report.pdf
			- report 334.pdf

For EISA reports the file format is a bit forgiving, but do still be careful; because it's more forgiving it sometimes doesn't report errors and may skip files not organized correctly. Again, the input directory should have directories within it that are named starting with a site ID. Within this, there are two options:
1. A directory containing 'Clean and send to client' in its name: in this case, reports may be placed directly into 'Clean and send to client', or into directories immediately within 'Clean and send to client'. This is not recursive, so further subdirectories will not be searched.
2. Alternatively, the 'layer' of subdirectories can be immediately within the site directory. That is, directories within the site folder that themselves have directories named with 'Clean and send to client'. Reports may then be placed within these 'Clean and send to client folders'. Subdirectories will not be searched.\

Note that in either case, reports must have their sub-site id within the file name. In the case that a file does not have a number in its name, it will be skipped and noted in `rename-info.txt`. If there are multiple numbers in the file name, the last one will be used. This is important, for example, in my testing a report was named `343 1st and 2nd Grade.pdf`, and the program tried to pull the '2' as the sub-site id. The reason we don't use the first number is in case a report is already renamed - the sub-site id will be last. For those familiar with Regular Expressions, the expression we use to pull the sub-site id is: `\D*(\d+)\D*(?!.*\d)`.

## Details of what it does
First, the script runs some initialization steps: creating rename-info.txt, opening the spreadsheet, and starting Tesseract. Then, it wil navigate through the input directory, finding reports in the places specified in [File Structure](/#File-Structure). For each report, it will use the Site Id (and, for EISA reports, the sub-site id), and search the location heirarchy spreadsheet to find the relevant row. From there, it will grab the Maximo Id, Location Number, and Site Name (headed on the spreadsheet as 'Site Description'). Then, to get the year, the program will open the first page of the pdf as an image and use Tesseract, an optical character recognition (OCR) library to pull the text from that page, then find the year within that (again, to those familiar with Regular Expressions, `(\d{2},\s*|\w+\s*)(20\d{2})`[^1]). We then put all this information together and copy the file, renamed, to the output directory.

[^1]: This is a year, 20##, preceded by either two digits and a comma or one or more words. As such, we'll capture either, for example, 'September 23, 2011', or just 'September 2011'. This is necessary because different types of reports format their dates differently.

## Changing the Code
The `.JAR` file is compiled and compressed, meaning it is not human-readable code. If you want to change how the program works, add new features or report types, or anything else, I have uploaded the program files to a github repository uncompiled. That github repository will also include this readme.md (and an HTML version in case you can't open the .md file), as well as the compiled `.JAR`.

The mentioned github repo is [here](https://github.com/Jaden-Unruh/Combined-Reports-Renaming-Tool).

## In the Github
The folder `report.renamer-multi` contains all of the java code as well as associated data and resource files. It does not contain all the dependency folders, though it does have maven references to them within `pom.xml` so they can be installed by other users. Hopefully, you can open this directory as a project in any IDE to easily edit the code.

All of the contents of `report.renamer-multi` are bundled within `Combined Report Renamer.JAR`. This is what is distributed to users, it cannot be edited but can be run to perform the script.

The other two files within the github are this `README.md`and an equivalent `README.html`. They are identical, just different file types in case a user can't open a .md file.
