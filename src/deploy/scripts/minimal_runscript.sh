#!/usr/bin/env bash
# ./minimal_runscript.sh

# Check if java exists
if [[ -z "$(which java)" ]]; then
    printf "Missing dependencies: Java\n"
    exit 1
fi

# Passed from the startscript
if [[ -z "$JEAK_JVM_ARGS" ]]; then
    JEAK_JVM_ARGS="-Xmx1G -Xms1G"
fi
# Passed from the startscript
if [[ -z "$JEAK_ARGS" ]]; then
    JEAK_ARGS=""
fi
# Optionally passed from the startscript
if [[ -z "$JEAK_EXECUTABLE" ]]; then
    if [[ -e "jeakbot.jar" ]]; then
        JEAK_EXECUTABLE="jeakbot.jar"
    else
        CANDS=($(ls jeakbot*.jar))
        if [[ 1 -lt "${#CANDS[@]}" ]]; then
            JEAK_EXECUTABLE=${CANDS[0]}
        fi
    fi
fi
if [[ -z "$JEAK_EXECUTABLE" ]]; then
    printf "Cannot find JEAKBOT_EXECUTABLE!\n"
    exit 1
fi

printf "[DJVMARGS] ${JEAK_JVM_ARGS}\n"
printf "[DARGS] ${JEAK_ARGS}\n"

java -cp .:libraries ${JEAK_JVM_ARGS} de.fearnixx.jeak.Main ${JEAK_ARGS}
exit $?