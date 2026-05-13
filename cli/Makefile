CC       ?= gcc
CFLAGS   ?= -std=c11 -Wall -Wextra -O2 \
            -D_DEFAULT_SOURCE -D_FORTIFY_SOURCE=2 \
            -fstack-protector-strong
LDFLAGS  ?=

BUILD_DIR = build
TARGET    = $(BUILD_DIR)/autotweaker
SRCS      = autotweaker.c cJSON.c
OBJS      = $(SRCS:%.c=$(BUILD_DIR)/%.o)
DEPS      = $(OBJS:%.o=%.d)

all: $(TARGET)

-include $(DEPS)

$(BUILD_DIR):
	mkdir -p $@

$(BUILD_DIR)/%.o: %.c | $(BUILD_DIR)
	$(CC) $(CFLAGS) -MMD -MP -c $< -o $@

$(TARGET): $(OBJS)
	$(CC) $(LDFLAGS) $^ -o $@

clean:
	rm -rf $(BUILD_DIR)

.PHONY: all clean
