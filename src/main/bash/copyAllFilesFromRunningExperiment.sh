sudo chmod 600 ~/.ssh/result_host_cert.pem


if [ -z "$(ls -A $1)" ]; then
   echo "Empty"
   exit 0
else
   echo "Not Empty"
   echo "$(du -sh $1)"


    # 1. loop through all the suggestions folders
    # 2. and if the beam-log.out file update is more than 60 seconds then we need to move the suggestion to the one level up in a tobecopied folder
    # 3. and then run scp command from that folder

    cd $1
    machine_name="$(ec2metadata --instance-id)"
    echo "Machine name: $machine_name"

    exp_id="${PWD##*/}"
    cd suggestions

    dir_date=`date +%s`

    to_copy="../to_copy"
    source_exp_dir="$to_copy"/"$exp_id"
    target_exp_dir="$source_exp_dir"_"$dir_date"
    source_suggestions_dir="$source_exp_dir"/suggestions

    sudo mkdir $to_copy
    sudo mkdir $source_exp_dir
    sudo mkdir $source_suggestions_dir

    echo "source_exp_dir: $source_exp_dir";
    echo "target_exp_dir: $target_exp_dir";
    echo "suggestions dir: $source_suggestions_dir";

    for d in */ ; do
        current=`date +%s`
        lastFileModified="${d::-1}/stopwatch.txt"
        last_modified=`stat -c "%Y" $lastFileModified`

        if [ -e $lastFileModified ]
        then
            if [ $(($current-$last_modified)) -gt 180 ]; then
                echo "moving dir ${d::-1}"
                #echo "mv ${d::-1} $source_dir/"
                #echo mv "${d::-1}" "$1/to_copy/$exp_id/suggestions/${d::-1}"
                sudo mv "${d::-1}" "$1/to_copy/$exp_id/suggestions/"
            else
                echo "${d::-1} in progress cur_date: $current - last_modified: $last_modified";
            fi
        else
            echo "${d::-1}/stopwatch.txt does not exist"
        fi

    done


    sourceExperimentDir="$1/to_copy/${exp_id}"
    copiedExperiementDir="$1/copied/"

    sudo mkdir "$1/copied"


    echo "sourceExperimentDir: $sourceExperimentDir"
    echo "copiedExperiementDir: $copiedExperiementDir"

    if [ -z "$(ls -A $sourceExperimentDir)" ]; then
       echo "Empty source directory $sourceExperimentDir"
       exit 0
    else
        echo "Copying the files... from $source_dir"
        sudo scp -i ~/.ssh/result_host_cert.pem -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -r $sourceExperimentDir ubuntu@$2:~/sigoptResults/
        echo "Copying completed..."
        sudo mv "$sourceExperimentDir" "$copiedExperiementDir"
        echo "Moved the suggestions from $sourceExperimentDir to $copiedExperiementDir"
        echo "Done.."
    fi
fi

