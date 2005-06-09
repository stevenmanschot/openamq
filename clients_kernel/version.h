/*==========================================================
 *
 *  version.h - version information for OpenAMQ base C clients
 *
 *  Should be the last file included in parent source program.
 *  This file is generated by Boom at configuration time.
 *  Copyright (c) 2004-2005 JPMorgan
 *==========================================================*/

#undef  VERSION         /*  Scrap any previous definitions  */
#undef  PRODUCT
#undef  COPYRIGHT
#undef  BUILDDATE
#undef  BUILDMODEL
#define VERSION         "0.8d5"
#define PRODUCT         "OpenAMQ base C clients/0.8d5"
#define COPYRIGHT       "Copyright (c) 2004-2005 JPMorgan"
#define BUILDDATE       "2005/06/09 12:39:25"
#if DEBUG == 1
#   define BUILDMODEL   "Debug release for internal use only"
#else
#   define BUILDMODEL   "Production release"
#endif
/*  Embed the version information in the resulting binary   */
char *openamq_base_c_clients_version_start = "VeRsIoNsTaRt:openamq_base_c_clients";
char *openamq_base_c_clients_version = VERSION;
char *openamq_base_c_clients_product = PRODUCT;
char *openamq_base_c_clients_copyright = COPYRIGHT;
char *openamq_base_c_clients_builddate = BUILDDATE;
char *openamq_base_c_clients_buildmodel = BUILDMODEL;
char *openamq_base_c_clients_version_end = "VeRsIoNeNd:openamq_base_c_clients";
