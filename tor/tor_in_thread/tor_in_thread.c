#include "tor_in_thread.h"

#include <stdlib.h>
#include <pthread.h>
#include <tor_api.h>

struct tor_in_thread_args {
    int argc;
    char **argv;
};
typedef struct tor_in_thread_args tor_in_thread_args;

void *tor_in_thread_start(void *ptr) {
    tor_in_thread_args *args = (tor_in_thread_args *) ptr;

    tor_main_configuration_t *cfg = tor_main_configuration_new();
    tor_main_configuration_set_command_line(cfg, args->argc, args->argv);

    free(args);

    tor_run_main(cfg);

    tor_main_configuration_free(cfg);

    return NULL;
}

void tor_in_thread(int argc, char **argv) {
    tor_in_thread_args *args = (tor_in_thread_args *) malloc(sizeof(tor_in_thread_args));
    args->argc = argc;
    args->argv = argv;

    pthread_t thread_id;
    pthread_create(&thread_id, NULL, tor_in_thread_start, (void *) args);
}
