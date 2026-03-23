# Contributors:
#     Rémi Dutil (2026) - updated for ManaDesk creation and Eclipse 2.0 migration
# 	  Jason Grammenos (2026) - merged in change_version.sh
#!/bin/bash
show_help(){
	echo "This script builds ManaDesk, and optional a new release of ManaDesk"
	echo "standard usage: bash build.sh -v (new_version_number) -r"
	echo "with new_version_number being a semantic version number" 
	echo "------"
	echo "-v set new version number"
	echo "-r create a new release"
}
change_version (){
	local OLD_VERSION=$1
	local NEW_VERSION=$2
	echo "Updating version from ${OLD_VERSION} to ${NEW_VERSION}."
	find . -name "*.xml" -not \( -path "./updates/*" -prune \) | xargs sed -i -e "s/$OLD_VERSION-SNAPSHOT/$NEW_VERSION-SNAPSHOT/" -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
	find . -name "*.MF" | xargs sed -i -e "s/$OLD_VERSION.qualifier/$NEW_VERSION.qualifier/"
	find . -name "*.properties" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
	find . -type f -name "*.product" | xargs sed -i -e "s/$OLD_VERSION/$NEW_VERSION/"
}

get_current_version (){
	local filename="./version.ini"
	local version_number=0
	version_number=$(cat "$filename" | cut -d "=" -f 2)
	echo "$version_number"
}

write_current_version(){
	local new_current_version=$1
	printf "current_version=${new_current_version}" > "./version.ini"
}

version=0.9.0
release=false
while getopts "h?v:r" opt; do
  case "$opt" in
    h|\?)
      show_help
      exit 0
      ;;
    v)  version=$OPTARG
      ;;
    r)  release=true
      ;;
  esac
done
shift $((OPTIND-1))

#build script, only works on linux
#needs maven 3.3.9 and java compiler 17+ installed
export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk-amd64/
PATH=$JAVA_HOME/bin:$PATH
if [ "$release" = true ] ; then
	# update the version
	current_version="$(get_current_version)"
	change_version "${current_version}" "${version}"
fi
mvn -f com.reflexit.magiccards.parent/pom.xml -Dmaven.test.skip=true clean verify
if [ "$release" = true ] ; then
	# setup release files for self update
	echo "Creating Release version ${version}"
	datestring=$(date '+%Y%m%d')
	time=$(date '+%H%M')
	targetdir=./updates/releases/${version}v${datestring}-${time}
	mkdir $targetdir
	# insert line into updates/0.x/compositeArtifacts.xml, and compositeContent.xml
	# create and destroy a venv, to install lxml and do the xml modifications
	python3 -m venv venv_buildscript
	source venv_buildscript/bin/activate
	python3 -m pip install lxml
	python3 build.py --version $version --date $datestring --time $time
	deactivate # exit venv
	rm -rf venv_buildscript
	# copy artifacts into created directory
	cp -R ./com.reflexit.magiccards.product/target/repository/features $targetdir/
	cp -R ./com.reflexit.magiccards.product/target/repository/plugins $targetdir/
	cp -R ./com.reflexit.magiccards.product/target/repository/artifacts.xml.xz $targetdir/
	cp -R ./com.reflexit.magiccards.product/target/repository/content.xml.xz $targetdir/
	cp -R ./com.reflexit.magiccards.product/target/repository/p2.index $targetdir/
	# extract the artifacts.xml from artifacts.xml.xz or artifacts.jar, the same with content.xml
	# run in subshell to not have cd affect current directory
	(cd ./com.reflexit.magiccards.product/target/repository && jar -xf artifacts.jar)
	(cd ./com.reflexit.magiccards.product/target/repository && jar -xf content.jar)
	cp ./com.reflexit.magiccards.product/target/repository/artifacts.xml $targetdir/ 
	cp ./com.reflexit.magiccards.product/target/repository/content.xml $targetdir/ 
	# set current version
	write_current_version "${version}"
fi
#This adds java to bundles, java runtime (1.8 +) has to be downloaded and extracted in $JAVADIR using this convention
# linux.gtk.x86_64/MagicAssistant/jre/
# win32.win32.x86_64/MagicAssistant/jre
# run script download_jre.sh to get it
JAVADIR=$HOME/java
cd com.reflexit.magiccards.product/target/products/

#this one only add it to .zip (win archives)
for i in *.zip; do
	TIMESTAMP=$(echo $i | sed -e 's/.*\.\([0-9]\+\)-.*/\1/')
	PLATFORM=$(echo $i | sed -e 's/.*-\(.*\)\.zip/\1/')
	#echo $PLATFORM
	JRE=$JAVADIR/$PLATFORM/MagicAssistant/jre
	if [ -d "$JRE" ]; then
		NEWZIP=$(echo $(basename $i .zip)-withjava.zip)
		cp $i $NEWZIP
		NEWZIP=$PWD/$NEWZIP
		(
		cd $JAVADIR/$PLATFORM
		zip -qq -u $NEWZIP -r MagicAssistant/jre || rm $NEWZIP
		test -f $NEWZIP && echo "Updated: $NEWZIP"
	)
        else
	        echo "Info: No java for $PLATFORM"
	fi
done
#this one only add it to .tar.gz (linux archives)
for i in *.tar.gz; do
	TIMESTAMP=$(echo $i | sed -e 's/.*\.\([0-9]\+\)-.*/\1/')
	PLATFORM=$(echo $i | sed -e 's/.*-\(.*\)\.tar.gz/\1/')
	#echo $PLATFORM
	JRE=$JAVADIR/$PLATFORM/MagicAssistant/jre
	if [ -d "$JRE" ]; then
		NEWZIP=$(echo $(basename $i .tar.gz)-withjava.tar.gz)
		NEWZIP=$PWD/$NEWZIP
		NEWTAR=$PWD/$(echo $(basename $i .tar.gz)-withjava.tar)
		cp $i $NEWZIP
		(
		gunzip $NEWZIP
		cd $JAVADIR/$PLATFORM
		tar uf $NEWTAR  MagicAssistant/jre && gzip -9 $NEWTAR || rm $NEWTAR $NEWZIP
		test -f $NEWZIP && echo "Updated: $NEWZIP"
		)
        else
	        echo "Info: No java for $PLATFORM"
	fi
done
