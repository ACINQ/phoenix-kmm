#include "tor_in_thread.h"

#include <tor_api.h>

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <pthread.h>

struct tor_in_thread_args {
    int argc;
    char **argv;
};
typedef struct tor_in_thread_args tor_in_thread_args;

int tor_in_thread_is_running = 0;

void *tor_in_thread_exec(void *ptr) {
    printf("Tor thread has started.\n");

    tor_in_thread_args *args = (tor_in_thread_args *) ptr;

    tor_main_configuration_t *cfg = tor_main_configuration_new();
    tor_main_configuration_set_command_line(cfg, args->argc, args->argv);

    tor_run_main(cfg);

    tor_main_configuration_free(cfg);

    for (int i = 0 ; i < args->argc ; ++i) free(args->argv[i]);
    free(args);

    tor_in_thread_is_running = 0;

    printf("Tor thread has ended.\n");

    return NULL;
}

void tor_in_thread_start(int argc, char **argv) {
    if (tor_in_thread_is_running == 1) {
        printf("Cannot start Tor: it is already running!\n");
        return ;
    }

    tor_in_thread_args *args = (tor_in_thread_args *) malloc(sizeof(tor_in_thread_args));
    args->argc = argc;

    // Copy arguments to make sure their memory remains available during the entire tor_run_main life.
    // This is because the documentation of tor_main_configuration_set_command_line specifies:
    // "The contents of the argv pointer must remain unchanged until tor_run_main() has finished and you call tor_main_configuration_free()."
    args->argv = (char **) malloc(argc * sizeof(char*));
    for (int i = 0 ; i < argc ; ++i) args->argv[i] = strdup(argv[i]);

    pthread_t thread_id;
    pthread_create(&thread_id, NULL, tor_in_thread_exec, (void *) args);
    tor_in_thread_is_running = 1;
}

int tor_in_thread_get_is_running() {
    return tor_in_thread_is_running;
}
