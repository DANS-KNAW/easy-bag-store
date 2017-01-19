This is a sequence of three bags. First a is added, then b and then c.
b is modified version of a, and c is modified further. Below the payload
of each bag is listed, and for each file in b and c how it relates to the
previous bags and how it is expected to be represented. The contents of
all the file is different, unless it is specifically said to be the same
as another file.

These things should all be checked in the unit tests, so this description
is slightly redundant. However, it may be useful as an overview.

a:
data/sub/u
data/sub/v
data/sub/w

data/x
data/y
data/z

b:
data/sub/u      unchanged               => reference in fetch.txt
[data/sub/v]    moved                   => not present here, no reference in fetch.txt
[data/sub/w]    deleted                 => not present here, no reference in fetch.txt

data/v          moved                   => reference in fetch.txt
data/x          unchanged               => reference in fetch.txt
data/y          changed                 => actual file
data/y-old      copy of y               => reference in fetch.txt
[data/z]        deleted                 => not present, no reference in fetch.txt

c:
data/sub/q      new file                => actual file
data/sub/w      restored file from a    => actual file if only b is ref-bag, reference in fetch.txt if a is also ref-bag

data/sub-copy/u the same as in b        => reference in fetch.txt to a (copied from b's fetch.txt)

data/p          new file                => actual file
data/x          unchanged               => reference in fetch.txt to a
data/y          unchanged               => reference in fetch.txt to b
data/y-old      unchanged               => reference in fetch.txt to a
[data/v]        deleted                 => not present here, no reference in fetch.txt
data/z          restored file from a    => actual file if only b is ref-bag, reference in fetch.txt if a is also ref-bag


