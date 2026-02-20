class ExtensionLogger(object):
    def __init__(self, callbacks, prefix):
        self._callbacks = callbacks
        self._prefix = prefix

    def info(self, message):
        if self._callbacks is None:
            return
        self._callbacks.printOutput("%s %s" % (self._prefix, message))

    def error(self, message):
        if self._callbacks is None:
            return
        self._callbacks.printError("%s %s" % (self._prefix, message))
