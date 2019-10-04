#!/usr/bin/env bash

# Record the time, so we will be able to see when this version of the box was created
sudo /bin/sh -c 'date > /etc/vagrant_box_build_time'

# Bring installed packages up-to-date, but do not uninstall obsolete packages
#sudo yum update -y

# Put back the vagrant public key, and make sure the rights of the ssh config files are set correctly
sudo curl -Lo /home/vagrant/.ssh/authorized_keys 'https://raw.github.com/mitchellh/vagrant/master/keys/vagrant.pub'
sudo chmod 0600 /home/vagrant/.ssh/authorized_keys
sudo chown -R vagrant:vagrant /home/vagrant/.ssh

# Not completely sure what this does, it *should* compact the initial space used for the virtual hard disk.
sudo dd if=/dev/zero of=/EMPTY bs=1M
sudo rm -f /EMPTY

# Power off the guest before packaging this with vagrant (on the host)
sudo poweroff
